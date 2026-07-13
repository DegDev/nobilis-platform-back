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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import io.github.degdev.engine.common.crypto.CryptoProperties;
import io.github.degdev.engine.common.crypto.CryptoService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit-level secret round-trip: a real {@link CryptoService} with a mocked repository proves that
 * {@link AiSecretStore#store} encrypts before the value reaches the row and {@link
 * AiSecretStore#read} decrypts on the way out — the same shape as {@code SettingsServiceTest}.
 */
class AiSecretStoreTest {

  private AiSecretRepository repository;
  private CryptoService cryptoService;
  private AiSecretStore secretStore;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AiSecretRepository.class);
    cryptoService = new CryptoService(new CryptoProperties(CryptoKeyGenerator.generateBase64Key()));
    secretStore = new AiSecretStore(repository, cryptoService);
    when(repository.save(any(AiSecret.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void storeEncryptsBeforePersisting() {
    secretStore.store("default.ollama.api-key", "sk-hunter2");

    ArgumentCaptor<AiSecret> captor = ArgumentCaptor.forClass(AiSecret.class);
    Mockito.verify(repository).save(captor.capture());
    AiSecret saved = captor.getValue();
    assertThat(saved.getRef()).isEqualTo("default.ollama.api-key");
    assertThat(saved.getValue()).startsWith("v1:").doesNotContain("sk-hunter2");
    assertThat(cryptoService.decrypt(saved.getValue())).isEqualTo("sk-hunter2");
  }

  @Test
  void readReturnsDecryptedPlaintext() {
    AiSecret stored =
        new AiSecret("default.ollama.api-key", cryptoService.encrypt("sk-hunter2"), Instant.now());
    when(repository.findById("default.ollama.api-key")).thenReturn(Optional.of(stored));

    assertThat(secretStore.read("default.ollama.api-key")).contains("sk-hunter2");
  }

  @Test
  void readMissingRefReturnsEmpty() {
    when(repository.findById("absent")).thenReturn(Optional.empty());

    assertThat(secretStore.read("absent")).isEmpty();
  }
}
