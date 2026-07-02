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
package io.github.degdev.engine.auth.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.degdev.engine.auth.account.Account;
import io.github.degdev.engine.auth.account.AccountRepository;
import io.github.degdev.engine.auth.account.AccountStatus;
import io.github.degdev.engine.auth.account.PermissionResolver;
import jakarta.persistence.EntityManager;
import java.util.Set;
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
 * Integration slice against a real PostgreSQL 18 (Testcontainers). Proves the B0.2 DoD: Flyway
 * applies {@code V3} so the three RBAC tables exist, {@code ddl-auto=validate} confirms the
 * entities match the migrated schema, an account's effective permissions resolve through the full
 * walk {@code account → account_role → role → role_permission}, and the {@code uq_role_code} unique
 * constraint rejects a duplicate role code.
 *
 * <p>{@code @Transactional} keeps one session per test so the lazy role/permission collections load
 * on read-back and each method rolls back in isolation; {@link EntityManager#flush()} + {@link
 * EntityManager#clear()} force a genuine reload from the database rather than the first-level
 * cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class RolePermissionIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private AccountRepository accountRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flywayCreatedTheThreeRbacTables() {
    Integer tables =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables "
                + "where table_schema = 'public' "
                + "and table_name in ('role', 'role_permission', 'account_role')",
            Integer.class);

    assertThat(tables).isEqualTo(3);
  }

  @Test
  void effectivePermissionsResolveThroughRoles() {
    Role admin = new Role("platform-admin", "Platform administrator");
    admin.addPermission(EnginePermissions.SETTINGS_MANAGE);
    admin.addPermission(EnginePermissions.ACCOUNT_MANAGE);
    roleRepository.save(admin);

    Account account = new Account(AccountStatus.ACTIVE);
    account.addRole(admin);
    Long accountId = accountRepository.save(account).getId();

    // Force a real reload from the database, not the first-level cache.
    entityManager.flush();
    entityManager.clear();

    Account reloaded = accountRepository.findById(accountId).orElseThrow();
    Set<String> effective = PermissionResolver.resolve(reloaded);

    assertThat(effective)
        .containsExactlyInAnyOrder(
            EnginePermissions.SETTINGS_MANAGE, EnginePermissions.ACCOUNT_MANAGE);
  }

  @Test
  void duplicateRoleCodeIsRejected() {
    roleRepository.saveAndFlush(new Role("duplicate-code", "First"));

    assertThatThrownBy(() -> roleRepository.saveAndFlush(new Role("duplicate-code", "Second")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
