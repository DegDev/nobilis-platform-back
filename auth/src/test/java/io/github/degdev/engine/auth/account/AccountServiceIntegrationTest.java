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

import io.github.degdev.engine.auth.role.Role;
import io.github.degdev.engine.auth.role.RoleRepository;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Direct integration slice of {@link AccountService} against a real PostgreSQL 18 (Testcontainers),
 * with {@code open-in-view=false} and NO ambient {@code @Transactional} — so the service's own
 * transaction alone must materialize the {@link AccountDto}. If it left {@code account_realm},
 * {@code account_role} or the identity rows lazy, reading the returned read-model here would blow
 * up with a {@code LazyInitializationException}; a clean read proves the in-transaction mapping.
 * Also pins the unknown-reference contract at its source (the domain exceptions the admin layer
 * maps to {@code 400}) and that the read-model structurally cannot carry a secret hash.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.jpa.open-in-view=false")
@Testcontainers
class AccountServiceIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountIdentityRepository identityRepository;
  @Autowired private RoleRepository roleRepository;

  @Test
  void getMaterializesTheLazyCollectionsAndIdentitiesWithoutASecretHash() {
    Role role = roleRepository.save(new Role("svc-viewer", "Service viewer"));
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);
    account.addRole(role);
    long id = accountRepository.save(account).getId();
    identityRepository.save(
        new AccountIdentity(account, ProviderType.EMAIL, "svc@example.org", "irrelevant-hash"));

    AccountDto dto = accountService.get(id).orElseThrow();

    assertThat(dto.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(dto.realms()).containsExactly(Realm.ADMIN);
    assertThat(dto.roles())
        .singleElement()
        .satisfies(ref -> assertThat(ref.code()).isEqualTo("svc-viewer"));
    assertThat(dto.identities())
        .singleElement()
        .satisfies(
            ref -> {
              assertThat(ref.provider()).isEqualTo(ProviderType.EMAIL);
              assertThat(ref.externalId()).isEqualTo("svc@example.org");
            });
  }

  @Test
  void identityRefStructurallyCannotCarryASecretHash() {
    assertThat(AccountDto.IdentityRef.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("provider", "externalId");
  }

  @Test
  void updateReplacesStatusRealmsAndRoles() {
    Role first = roleRepository.save(new Role("svc-a", "A"));
    Role second = roleRepository.save(new Role("svc-b", "B"));
    long id = accountRepository.save(newAccount(Realm.CLIENT, first)).getId();

    AccountDto updated =
        accountService
            .update(id, AccountStatus.BLOCKED, Set.of("ADMIN"), Set.of(second.getId()))
            .orElseThrow();

    assertThat(updated.status()).isEqualTo(AccountStatus.BLOCKED);
    assertThat(updated.realms()).containsExactly(Realm.ADMIN);
    assertThat(updated.roles())
        .singleElement()
        .satisfies(ref -> assertThat(ref.code()).isEqualTo("svc-b"));
  }

  @Test
  void updateWithAnUnknownRealmThrows() {
    long id = accountRepository.save(newAccount(Realm.ADMIN)).getId();

    assertThatThrownBy(
            () -> accountService.update(id, AccountStatus.ACTIVE, Set.of("NOPE"), Set.of()))
        .isInstanceOf(UnknownRealmException.class)
        .hasMessageContaining("NOPE");
  }

  @Test
  void updateWithAnUnknownRoleIdThrows() {
    long id = accountRepository.save(newAccount(Realm.ADMIN)).getId();

    assertThatThrownBy(
            () ->
                accountService.update(id, AccountStatus.ACTIVE, Set.of("ADMIN"), Set.of(77777777L)))
        .isInstanceOf(UnknownRoleException.class)
        .hasMessageContaining("77777777");
  }

  @Test
  void getIsEmptyForAnUnknownId() {
    assertThat(accountService.get(66666666L)).isEqualTo(Optional.empty());
  }

  private static Account newAccount(Realm realm, Role... roles) {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(realm);
    Arrays.stream(roles).forEach(account::addRole);
    return account;
  }
}
