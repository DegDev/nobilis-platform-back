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
package io.github.degdev.engine.admin.roles;

import io.github.degdev.engine.auth.role.Role;
import java.util.Set;

/**
 * The API view of a {@link Role}: its id, immutable {@code code}, label and the permission values
 * it grants.
 *
 * @param id the role id
 * @param code the unique, immutable business key
 * @param name the human-readable label
 * @param permissions the permission values the role grants
 */
public record RoleDto(Long id, String code, String name, Set<String> permissions) {

  /**
   * Projects an entity to its API view.
   *
   * @param role the stored role (with its permissions loaded)
   * @return the API view
   */
  public static RoleDto from(Role role) {
    return new RoleDto(role.getId(), role.getCode(), role.getName(), role.getPermissions());
  }
}
