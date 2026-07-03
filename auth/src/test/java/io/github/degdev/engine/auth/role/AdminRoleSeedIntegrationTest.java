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

import io.github.degdev.engine.auth.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice for pass-4a's persistence foundation against a real PostgreSQL 18
 * (Testcontainers). Proves that, with a JPA {@code EntityManagerFactory} present, auth's Spring
 * Data repositories are beans (discovered via {@link
 * io.github.degdev.engine.auth.persistence.AuthPersistenceAutoConfiguration}'s auto-configuration
 * package), and that Flyway's {@code V4} seed created the engine's default {@code ADMIN} role
 * carrying exactly the two engine permissions.
 *
 * <p>{@code @Transactional} keeps one session open so the lazy {@code role_permission} collection
 * loads on read-back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class AdminRoleSeedIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private RoleRepository roleRepository;
  @Autowired private AccountRepository accountRepository;

  @Test
  void authRepositoriesAreBeansWhenAnEntityManagerFactoryIsPresent() {
    assertThat(roleRepository).isNotNull();
    assertThat(accountRepository).isNotNull();
  }

  @Test
  void v4SeedsTheDefaultAdminRoleWithTheTwoEnginePermissions() {
    Role admin = roleRepository.findByCode("ADMIN").orElseThrow();

    assertThat(admin.getName()).isEqualTo("Administrator");
    assertThat(admin.getPermissions())
        .containsExactlyInAnyOrder(
            EnginePermissions.ACCOUNT_MANAGE, EnginePermissions.SETTINGS_MANAGE);
  }
}
