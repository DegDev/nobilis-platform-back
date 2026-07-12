/*
 * Copyright (c) 2026 Dmitri Puscas (DegDev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.degdev.engine.ai.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.ai.provider.AiFieldCategory;
import io.github.degdev.engine.ai.provider.AiFieldType;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import io.github.degdev.engine.ai.provider.AiProviderField;
import io.github.degdev.engine.ai.provider.AiProviderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit-level: mocked repositories and a mocked {@link AiProviderDefaults} prove {@code resolve}'s
 * default-vs-saved merge and {@code save}'s catalog-validation guard clauses, without needing a
 * real database or a Hibernate-assigned id (every rejection path throws before persistence, so a
 * plain unpersisted {@link io.github.degdev.engine.ai.profile.AiProfile} — id {@code null} — is
 * enough). The full save-then-resolve round trip against real generated ids is proven by {@code
 * AiServiceIntegrationTest} instead.
 */
class AiProfileServiceTest {

  private static final String PURPOSE = "default";
  private static final String PROVIDER = "ollama";

  private AiProfileRepository profileRepository;
  private AiProfileParamRepository paramRepository;
  private AiProviderRepository providerRepository;
  private AiProviderDefaults providerDefaults;
  private AiProfileService service;

  @BeforeEach
  void setUp() {
    profileRepository = Mockito.mock(AiProfileRepository.class);
    paramRepository = Mockito.mock(AiProfileParamRepository.class);
    providerRepository = Mockito.mock(AiProviderRepository.class);
    providerDefaults = Mockito.mock(AiProviderDefaults.class);
    service =
        new AiProfileService(
            profileRepository, paramRepository, providerRepository, providerDefaults);
  }

  @Test
  void resolveWithNoSavedProfileFallsBackToCatalogDefaults() {
    when(profileRepository.findByPurpose(PURPOSE)).thenReturn(Optional.empty());
    when(providerDefaults.defaultProviderCode(PURPOSE)).thenReturn(PROVIDER);
    when(providerDefaults.defaultParams(PURPOSE, PROVIDER)).thenReturn(Map.of("model", "llama3"));

    ResolvedAiProfile resolved = service.resolve(PURPOSE);

    assertThat(resolved.providerCode()).isEqualTo(PROVIDER);
    assertThat(resolved.params()).containsExactlyEntriesOf(Map.of("model", "llama3"));
    Mockito.verifyNoInteractions(paramRepository);
  }

  @Test
  void resolveWithSavedProfileOverridesCatalogDefaults() {
    AiProfile saved = new AiProfile(PURPOSE, null, PROVIDER);
    when(profileRepository.findByPurpose(PURPOSE)).thenReturn(Optional.of(saved));
    when(providerDefaults.defaultParams(PURPOSE, PROVIDER))
        .thenReturn(Map.of("model", "llama3", "temperature", "0.7"));
    when(paramRepository.findByIdProfileId(any()))
        .thenReturn(List.of(new AiProfileParam(new AiProfileParamId(null, "temperature"), "0.2")));

    ResolvedAiProfile resolved = service.resolve(PURPOSE);

    assertThat(resolved.providerCode()).isEqualTo(PROVIDER);
    assertThat(resolved.params())
        .containsEntry("model", "llama3")
        .containsEntry("temperature", "0.2");
  }

  @Test
  void saveRejectsAnUnknownProvider() {
    when(providerRepository.existsById("not-a-provider")).thenReturn(false);

    assertThatThrownBy(() -> service.save(PURPOSE, "not-a-provider", Map.of()))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void saveRejectsAnUnknownFieldKey() {
    when(providerRepository.existsById(PROVIDER)).thenReturn(true);
    when(providerDefaults.fieldsByKey(PURPOSE, PROVIDER)).thenReturn(Map.of());

    assertThatThrownBy(() -> service.save(PURPOSE, PROVIDER, Map.of("bogus-field", "x")))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void saveRejectsASecretFieldSubmittedAsAPlainParam() {
    when(providerRepository.existsById(PROVIDER)).thenReturn(true);
    AiProviderField secretField =
        new AiProviderField(
            PURPOSE,
            PROVIDER,
            "api-key",
            AiFieldCategory.SECRET,
            AiFieldType.STRING,
            true,
            null,
            null,
            null,
            0);
    when(providerDefaults.fieldsByKey(PURPOSE, PROVIDER))
        .thenReturn(Map.of("api-key", secretField));

    assertThatThrownBy(() -> service.save(PURPOSE, PROVIDER, Map.of("api-key", "sk-123")))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void saveRejectsANonNumericValueForANumberField() {
    when(providerRepository.existsById(PROVIDER)).thenReturn(true);
    AiProviderField temperature =
        new AiProviderField(
            PURPOSE,
            PROVIDER,
            "temperature",
            AiFieldCategory.OPERATIONAL,
            AiFieldType.NUMBER,
            true,
            "0.7",
            BigDecimal.ZERO,
            BigDecimal.ONE,
            2);
    when(providerDefaults.fieldsByKey(PURPOSE, PROVIDER))
        .thenReturn(Map.of("temperature", temperature));

    assertThatThrownBy(() -> service.save(PURPOSE, PROVIDER, Map.of("temperature", "not-a-number")))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void saveRejectsAnOutOfBoundsNumber() {
    when(providerRepository.existsById(PROVIDER)).thenReturn(true);
    AiProviderField temperature =
        new AiProviderField(
            PURPOSE,
            PROVIDER,
            "temperature",
            AiFieldCategory.OPERATIONAL,
            AiFieldType.NUMBER,
            true,
            "0.7",
            BigDecimal.ZERO,
            BigDecimal.ONE,
            2);
    when(providerDefaults.fieldsByKey(PURPOSE, PROVIDER))
        .thenReturn(Map.of("temperature", temperature));

    assertThatThrownBy(() -> service.save(PURPOSE, PROVIDER, Map.of("temperature", "5")))
        .isInstanceOf(AiProfileException.class);
  }
}
