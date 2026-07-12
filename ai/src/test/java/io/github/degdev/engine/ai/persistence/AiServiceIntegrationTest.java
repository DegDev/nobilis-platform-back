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
package io.github.degdev.engine.ai.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.degdev.engine.ai.profile.AiProfileException;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.AiSecretStore;
import io.github.degdev.engine.ai.profile.ResolvedAiProfile;
import io.github.degdev.engine.ai.provider.AiFieldCategory;
import io.github.degdev.engine.ai.provider.AiFieldDescriptor;
import io.github.degdev.engine.ai.provider.AiFieldType;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers), crypto master key configured.
 * Proves slice 2's functional DoD: the catalog descriptor for {@code (default, ollama)} matches the
 * slice-1 seed exactly (Fork 5's native-wire field set), resolving a purpose with no saved profile
 * falls back to those catalog defaults, saving overrides only the submitted fields while leaving
 * the rest at their defaults, the catalog-validation guard rejects both an unknown field key and an
 * out-of-bounds number, and a secret round-trips through {@link AiSecretStore} via a real {@link
 * io.github.degdev.engine.common.crypto.CryptoService}.
 */
@SpringBootTest
@Testcontainers
class AiServiceIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_MASTER_KEY = CryptoKeyGenerator.generateBase64Key();

  @Autowired private AiProviderDefaults providerDefaults;
  @Autowired private AiProfileService profileService;
  @Autowired private AiSecretStore secretStore;

  /** Supplies the crypto master key at runtime so no key value ever sits in a properties file. */
  @DynamicPropertySource
  static void cryptoProperties(DynamicPropertyRegistry registry) {
    registry.add("nobilis.crypto.master-key", () -> TEST_MASTER_KEY);
  }

  @Test
  void fieldDescriptorMatchesTheOllamaCatalogSeed() {
    List<AiFieldDescriptor> fields = providerDefaults.fields("default", "ollama");

    assertThat(fields)
        .extracting(AiFieldDescriptor::fieldKey)
        .containsExactly("base-url", "model", "temperature", "top_p", "num_predict", "no-think");

    AiFieldDescriptor baseUrl = fields.get(0);
    assertThat(baseUrl.category()).isEqualTo(AiFieldCategory.INFRA);
    assertThat(baseUrl.type()).isEqualTo(AiFieldType.STRING);
    assertThat(baseUrl.editable()).isFalse();
    assertThat(baseUrl.defaultValue()).isEqualTo("http://localhost:11434");

    AiFieldDescriptor temperature = fields.get(2);
    assertThat(temperature.category()).isEqualTo(AiFieldCategory.OPERATIONAL);
    assertThat(temperature.type()).isEqualTo(AiFieldType.NUMBER);
    assertThat(temperature.minValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(temperature.maxValue()).isEqualByComparingTo(BigDecimal.ONE);

    AiFieldDescriptor noThink = fields.get(5);
    assertThat(noThink.category()).isEqualTo(AiFieldCategory.OPERATIONAL);
    assertThat(noThink.type()).isEqualTo(AiFieldType.BOOLEAN);
    assertThat(noThink.defaultValue()).isEqualTo("true");
  }

  @Test
  void resolveWithNoSavedProfileFallsBackToOllamaDefaults() {
    ResolvedAiProfile resolved = profileService.resolve("default");

    assertThat(resolved.providerCode()).isEqualTo("ollama");
    assertThat(resolved.params())
        .containsEntry("base-url", "http://localhost:11434")
        .containsEntry("model", "llama3")
        .containsEntry("temperature", "0.7")
        .containsEntry("top_p", "0.9")
        .containsEntry("num_predict", "512")
        .containsEntry("no-think", "true");
  }

  @Test
  void saveOverridesOnlySubmittedFields() {
    profileService.save("default", "ollama", Map.of("model", "llama3.1", "temperature", "0.5"));

    ResolvedAiProfile resolved = profileService.resolve("default");

    assertThat(resolved.providerCode()).isEqualTo("ollama");
    assertThat(resolved.params())
        .containsEntry("model", "llama3.1")
        .containsEntry("temperature", "0.5")
        .containsEntry("top_p", "0.9")
        .containsEntry("num_predict", "512");
  }

  @Test
  void saveRejectsAnUnknownFieldKey() {
    assertThatThrownBy(() -> profileService.save("default", "ollama", Map.of("bogus-field", "x")))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void saveRejectsAnOutOfBoundsNumber() {
    assertThatThrownBy(() -> profileService.save("default", "ollama", Map.of("temperature", "5")))
        .isInstanceOf(AiProfileException.class);
  }

  @Test
  void secretRoundTripsThroughRealCrypto() {
    secretStore.store("default.ollama.api-key", "sk-hunter2");

    assertThat(secretStore.read("default.ollama.api-key")).contains("sk-hunter2");
  }
}
