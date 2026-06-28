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

import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers). Proves the milestone DoD: Flyway
 * applies {@code V1} so the {@code setting} table exists, secret values land in the column as
 * {@code v1:}-prefixed ciphertext (never plaintext) yet read back decrypted, and {@code BaseEntity}
 * auditing fills the timestamps. {@code ddl-auto=validate} additionally proves entity↔migration
 * parity.
 */
@SpringBootTest
@Testcontainers
class SettingsFlywayIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  // A fresh AES-256 key generated in-memory for this test run. It is never written to any committed
  // file, mirroring the production contract that the master key comes from the environment only.
  private static final String TEST_MASTER_KEY = CryptoKeyGenerator.generateBase64Key();

  @Autowired private SettingsService settingsService;
  @Autowired private SettingRepository settingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Supplies the crypto master key at runtime so no key value ever sits in a properties file. */
  @DynamicPropertySource
  static void cryptoProperties(DynamicPropertyRegistry registry) {
    registry.add("nobilis.crypto.master-key", () -> TEST_MASTER_KEY);
  }

  @Test
  void flywayBaselineCreatedTheSettingTable() {
    Integer tables =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables "
                + "where table_schema = 'public' and table_name = 'setting'",
            Integer.class);

    assertThat(tables).isEqualTo(1);
  }

  @Test
  void secretSettingIsStoredEncryptedAndReadBackDecrypted() {
    settingsService.set("bank.password", "hunter2", true);

    // Raw column: ciphertext with the v1: prefix, never the plaintext.
    String rawColumn =
        jdbcTemplate.queryForObject(
            "select value from setting where key = ?", String.class, "bank.password");
    assertThat(rawColumn).startsWith("v1:").doesNotContain("hunter2");

    // Service read: decrypted plaintext.
    assertThat(settingsService.get("bank.password")).contains("hunter2");
  }

  @Test
  void nonSecretSettingIsStoredAsPlaintext() {
    settingsService.set("portal.title", "Nobilis", false);

    String rawColumn =
        jdbcTemplate.queryForObject(
            "select value from setting where key = ?", String.class, "portal.title");

    assertThat(rawColumn).isEqualTo("Nobilis");
    assertThat(settingsService.get("portal.title")).contains("Nobilis");
  }

  @Test
  void auditingPopulatesTimestamps() {
    Setting saved = settingsService.set("audited.key", "value", false);

    Setting reloaded = settingRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
    assertThat(reloaded.getVersion()).isNotNull();
  }
}
