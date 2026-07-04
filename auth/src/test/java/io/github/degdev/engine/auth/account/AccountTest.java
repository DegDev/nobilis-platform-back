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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Account}'s realm/role write surface: the add/remove/replace round-trips a
 * management service relies on, and the unmodifiable-view guarantee the replace ops must respect.
 */
class AccountTest {

  @Test
  void addRealmIsIdempotent() {
    Account account = new Account(AccountStatus.ACTIVE);

    account.addRealm(Realm.ADMIN);
    account.addRealm(Realm.ADMIN);

    assertThat(account.getRealms()).containsExactly(Realm.ADMIN);
  }

  @Test
  void removeRealmRevokesAndIsANoOpWhenAbsent() {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);
    account.addRealm(Realm.CLIENT);

    account.removeRealm(Realm.ADMIN);
    account.removeRealm(Realm.ADMIN);

    assertThat(account.getRealms()).containsExactly(Realm.CLIENT);
  }

  @Test
  void replaceRealmsSwapsTheWholeSet() {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);

    account.replaceRealms(Set.of(Realm.CLIENT));

    assertThat(account.getRealms()).containsExactly(Realm.CLIENT);
  }

  @Test
  void replaceRealmsWithEmptyClears() {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);

    account.replaceRealms(Set.of());

    assertThat(account.getRealms()).isEmpty();
  }

  @Test
  void addRemoveReplaceRolesRoundTrip() {
    Account account = new Account(AccountStatus.ACTIVE);
    Role admin = new Role("ADMIN", "Administrator");
    Role editor = new Role("EDITOR", "Editor");

    account.addRole(admin);
    account.addRole(admin);
    assertThat(account.getRoles()).containsExactly(admin);

    account.addRole(editor);
    account.removeRole(admin);
    assertThat(account.getRoles()).containsExactly(editor);

    account.replaceRoles(Set.of(admin));
    assertThat(account.getRoles()).containsExactly(admin);
  }

  @Test
  void getRealmsReturnsAnUnmodifiableView() {
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRealm(Realm.ADMIN);

    assertThatThrownBy(() -> account.getRealms().add(Realm.CLIENT))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
