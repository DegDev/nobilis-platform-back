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

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Role}'s permission write surface: the add/remove/replace round-trips a
 * management service relies on, and the unmodifiable-view guarantee the replace op must respect.
 */
class RoleTest {

  @Test
  void addPermissionIsIdempotent() {
    Role role = new Role("ADMIN", "Administrator");

    role.addPermission(EnginePermissions.ACCOUNT_MANAGE);
    role.addPermission(EnginePermissions.ACCOUNT_MANAGE);

    assertThat(role.getPermissions()).containsExactly(EnginePermissions.ACCOUNT_MANAGE);
  }

  @Test
  void removePermissionRevokesAndIsANoOpWhenAbsent() {
    Role role = new Role("ADMIN", "Administrator");
    role.addPermission(EnginePermissions.ACCOUNT_MANAGE);
    role.addPermission(EnginePermissions.SETTINGS_MANAGE);

    role.removePermission(EnginePermissions.ACCOUNT_MANAGE);
    role.removePermission(EnginePermissions.ACCOUNT_MANAGE);

    assertThat(role.getPermissions()).containsExactly(EnginePermissions.SETTINGS_MANAGE);
  }

  @Test
  void replacePermissionsSwapsTheWholeSet() {
    Role role = new Role("ADMIN", "Administrator");
    role.addPermission(EnginePermissions.ACCOUNT_MANAGE);

    role.replacePermissions(Set.of(EnginePermissions.SETTINGS_MANAGE));

    assertThat(role.getPermissions()).containsExactly(EnginePermissions.SETTINGS_MANAGE);
  }

  @Test
  void getPermissionsReturnsAnUnmodifiableView() {
    Role role = new Role("ADMIN", "Administrator");
    role.addPermission(EnginePermissions.ACCOUNT_MANAGE);

    assertThatThrownBy(() -> role.getPermissions().add(EnginePermissions.SETTINGS_MANAGE))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
