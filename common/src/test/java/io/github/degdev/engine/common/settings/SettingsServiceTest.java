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
package io.github.degdev.engine.common.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import io.github.degdev.engine.common.crypto.CryptoProperties;
import io.github.degdev.engine.common.crypto.CryptoService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit-level secret round-trip: a real {@link CryptoService} with a mocked repository proves that
 * secrets are encrypted before they reach the row and decrypted on the way out, while non-secret
 * values pass through untouched.
 */
class SettingsServiceTest {

  private SettingRepository repository;
  private CryptoService cryptoService;
  private SettingsService settingsService;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(SettingRepository.class);
    cryptoService = new CryptoService(new CryptoProperties(CryptoKeyGenerator.generateBase64Key()));
    settingsService = new SettingsService(repository, cryptoService);
    when(repository.save(any(Setting.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void setSecretEncryptsBeforePersisting() {
    when(repository.findByKey("bank.password")).thenReturn(Optional.empty());

    settingsService.set("bank.password", "hunter2", true);

    ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
    Mockito.verify(repository).save(captor.capture());
    Setting saved = captor.getValue();
    assertThat(saved.isSecret()).isTrue();
    assertThat(saved.getValue()).startsWith("v1:").doesNotContain("hunter2");
    // And it decrypts back to the original plaintext.
    assertThat(cryptoService.decrypt(saved.getValue())).isEqualTo("hunter2");
  }

  @Test
  void setNonSecretStoresPlaintext() {
    when(repository.findByKey("portal.title")).thenReturn(Optional.empty());

    settingsService.set("portal.title", "Nobilis", false);

    ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
    Mockito.verify(repository).save(captor.capture());
    assertThat(captor.getValue().getValue()).isEqualTo("Nobilis");
    assertThat(captor.getValue().isSecret()).isFalse();
  }

  @Test
  void getSecretReturnsDecryptedPlaintext() {
    Setting stored = new Setting("sms.token", cryptoService.encrypt("abc-123"), true);
    when(repository.findByKey("sms.token")).thenReturn(Optional.of(stored));

    assertThat(settingsService.get("sms.token")).contains("abc-123");
  }

  @Test
  void getNonSecretReturnsStoredValue() {
    when(repository.findByKey("portal.title"))
        .thenReturn(Optional.of(new Setting("portal.title", "Nobilis", false)));

    assertThat(settingsService.get("portal.title")).contains("Nobilis");
  }

  @Test
  void getMissingKeyReturnsEmpty() {
    when(repository.findByKey("absent")).thenReturn(Optional.empty());

    assertThat(settingsService.get("absent")).isEmpty();
  }

  @Test
  void setUpdatesExistingSetting() {
    Setting existing = new Setting("portal.title", "Old", false);
    when(repository.findByKey("portal.title")).thenReturn(Optional.of(existing));

    settingsService.set("portal.title", "New", false);

    assertThat(existing.getValue()).isEqualTo("New");
  }
}
