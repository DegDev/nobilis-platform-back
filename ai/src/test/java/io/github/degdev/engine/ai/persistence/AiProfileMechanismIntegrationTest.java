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

import io.github.degdev.engine.ai.profile.AiProfile;
import io.github.degdev.engine.ai.profile.AiProfileParam;
import io.github.degdev.engine.ai.profile.AiProfileParamId;
import io.github.degdev.engine.ai.profile.AiProfileParamRepository;
import io.github.degdev.engine.ai.profile.AiProfileRepository;
import io.github.degdev.engine.ai.profile.AiSecret;
import io.github.degdev.engine.ai.profile.AiSecretRepository;
import io.github.degdev.engine.ai.provider.AiProviderField;
import io.github.degdev.engine.ai.provider.AiProviderFieldRepository;
import io.github.degdev.engine.ai.provider.AiProviderRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers). Proves slice 1's DoD: Flyway
 * applies the seven {@code ai_*} tables, {@code ddl-auto=validate} confirms the entities match the
 * migrated schema (the context would fail to start otherwise — this is what proves {@code
 * AiPersistenceAutoConfiguration} mounted the entities/repositories), the Ollama catalog seed is
 * present with its native-wire-format field set (Fork 5), and a profile/param/secret round-trips.
 *
 * <p>{@code @Transactional} keeps one session per test so each method rolls back in isolation;
 * {@link EntityManager#flush()} + {@link EntityManager#clear()} force a genuine reload rather than
 * the first-level cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class AiProfileMechanismIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private AiProviderRepository aiProviderRepository;
  @Autowired private AiProviderFieldRepository aiProviderFieldRepository;
  @Autowired private AiProfileRepository aiProfileRepository;
  @Autowired private AiProfileParamRepository aiProfileParamRepository;
  @Autowired private AiSecretRepository aiSecretRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatedTheSevenAiTables() {
    Integer tables =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('ai_provider', 'ai_provider_purpose', 'ai_provider_field', "
                + "'ai_provider_field_option', 'ai_profile', 'ai_profile_param', 'ai_secret')",
            Integer.class);

    assertThat(tables).isEqualTo(7);
  }

  @Test
  void catalogSeedProvidesOllamaAndItsNativeWireFieldSet() {
    assertThat(aiProviderRepository.findById("ollama")).isPresent();

    List<AiProviderField> fields =
        aiProviderFieldRepository.findByPurposeAndProviderCodeOrderBySortOrder("default", "ollama");

    assertThat(fields)
        .extracting(AiProviderField::getFieldKey)
        .containsExactly("base-url", "model", "temperature", "top_p", "num_predict", "no-think");
  }

  @Test
  void profileParamAndSecretRoundTrip() {
    AiProfile profile = aiProfileRepository.save(new AiProfile("default", null, "ollama"));
    aiProfileParamRepository.save(
        new AiProfileParam(new AiProfileParamId(profile.getId(), "model"), "llama3"));
    aiSecretRepository.save(new AiSecret("default.ollama.api-key", "ciphertext", Instant.now()));

    entityManager.flush();
    entityManager.clear();

    assertThat(aiProfileRepository.findByPurpose("default")).isPresent();
    assertThat(aiProfileParamRepository.findByIdProfileId(profile.getId())).hasSize(1);
    assertThat(aiSecretRepository.findById("default.ollama.api-key")).isPresent();
  }

  @Test
  void duplicateProfilePurposeIsRejected() {
    aiProfileRepository.saveAndFlush(new AiProfile("dup", null, "ollama"));
    AiProfile second = new AiProfile("dup", null, "ollama");

    assertThatThrownBy(() -> aiProfileRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
