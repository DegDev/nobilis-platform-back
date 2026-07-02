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
package io.github.degdev.engine.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.Optional;
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
 * Integration slice against a real PostgreSQL 18 (Testcontainers). Proves the B0.1 DoD: Flyway
 * applies {@code V2} so the three tables exist, {@code ddl-auto=validate} confirms the entities
 * match the migrated schema (the context would fail to start otherwise), an account round-trips
 * with an attached identity and an assigned realm, and the {@code (provider_type, external_id)}
 * unique constraint rejects a duplicate identity.
 *
 * <p>{@code @Transactional} keeps one session per test so the lazy {@code account_realm} collection
 * loads on read-back and each method rolls back in isolation; {@link EntityManager#flush()} +
 * {@link EntityManager#clear()} force a genuine reload from the database rather than the
 * first-level cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class AccountIdentityRealmIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountIdentityRepository accountIdentityRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatedTheThreeAccountTables() {
    Integer tables =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables "
                + "where table_schema = 'public' "
                + "and table_name in ('account', 'account_identity', 'account_realm')",
            Integer.class);

    assertThat(tables).isEqualTo(3);
  }

  @Test
  void accountRoundTripsWithIdentityAndRealm() {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);
    Long accountId = accountRepository.save(account).getId();

    accountIdentityRepository.save(
        new AccountIdentity(account, ProviderType.TELEGRAM, "tg-123", null));

    // Force a real reload from the database, not the first-level cache.
    entityManager.flush();
    entityManager.clear();

    Account reloaded = accountRepository.findById(accountId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(reloaded.getRealms()).containsExactly(Realm.ADMIN);
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getVersion()).isNotNull();

    Optional<AccountIdentity> found =
        accountIdentityRepository.findByProviderTypeAndExternalId(ProviderType.TELEGRAM, "tg-123");
    assertThat(found).isPresent();
    assertThat(found.get().getAccount().getId()).isEqualTo(accountId);
    assertThat(found.get().getSecretHash()).isNull();
  }

  @Test
  void duplicateProviderExternalIdIsRejected() {
    Account first = accountRepository.save(new Account(AccountStatus.ACTIVE));
    accountIdentityRepository.saveAndFlush(
        new AccountIdentity(first, ProviderType.EMAIL, "dup@example.org", "hash-1"));

    Account second = accountRepository.save(new Account(AccountStatus.ACTIVE));

    assertThatThrownBy(
            () ->
                accountIdentityRepository.saveAndFlush(
                    new AccountIdentity(second, ProviderType.EMAIL, "dup@example.org", "hash-2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
