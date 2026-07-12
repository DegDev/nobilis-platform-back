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

import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.AiSecretStore;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers), NO crypto master key configured.
 * Proves {@link AiServiceAutoConfiguration}'s split gate: with a real {@code EntityManagerFactory}
 * but no {@code CryptoService}, {@link AiProviderDefaults} and {@link AiProfileService} still mount
 * (they only need JPA), but {@link AiSecretStore} does not — never a fail-fast boot, mirroring
 * {@code SettingsAutoConfiguration}'s two-collaborator gate.
 */
@SpringBootTest
@Testcontainers
class AiServiceAutoConfigurationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private ApplicationContext context;

  @Test
  void jpaOnlyServicesMountWithoutCrypto() {
    assertThat(context.getBeanNamesForType(AiProviderDefaults.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(AiProfileService.class)).hasSize(1);
  }

  @Test
  void secretStoreDoesNotMountWithoutCrypto() {
    assertThat(context.getBeanNamesForType(AiSecretStore.class)).isEmpty();
  }
}
