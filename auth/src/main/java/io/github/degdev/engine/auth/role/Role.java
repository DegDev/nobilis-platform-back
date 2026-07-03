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

import io.github.degdev.engine.common.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of permissions, admin-editable at runtime. A role's {@code code} is its stable
 * business key (unique); its {@code name} is a human label. The permissions it grants are held as a
 * value collection of plain strings (each the value of a permission constant — see {@link
 * EnginePermissions}), yielding the {@code role_permission} join table with a composite {@code
 * (role_id, permission)} key and no surrogate id — mirroring how {@code account_realm} is modelled.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on the mutable {@code name}.
 * Equality is by the {@code code} business key alone ({@code onlyExplicitlyIncluded}, {@code
 * callSuper = false}) — never the generated id — so it is stable across the lifecycle.
 * {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "role")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

  @EqualsAndHashCode.Include
  @Column(name = "code", nullable = false, unique = true, length = 64)
  private String code;

  @Setter
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @ElementCollection
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission", nullable = false, length = 128)
  private Set<String> permissions = new LinkedHashSet<>();

  /**
   * Creates a role with the given business key and label and no permissions yet.
   *
   * @param code the unique, immutable business key
   * @param name the human-readable label
   */
  public Role(String code, String name) {
    this.code = code;
    this.name = name;
  }

  /**
   * Grants a permission to this role (idempotent).
   *
   * @param permission the permission constant's value (see {@link EnginePermissions})
   */
  public void addPermission(String permission) {
    this.permissions.add(permission);
  }

  /**
   * Revokes a permission from this role (idempotent — a no-op if not granted).
   *
   * @param permission the permission constant's value (see {@link EnginePermissions})
   */
  public void removePermission(String permission) {
    this.permissions.remove(permission);
  }

  /**
   * Replaces this role's permissions with the given set in one operation. Mutates the managed
   * collection in place (clear + add) rather than reassigning it, so Hibernate's dirty-tracking and
   * the {@code role_permission} join stay correct.
   *
   * @param newPermissions the new, complete set of permission values
   */
  public void replacePermissions(Set<String> newPermissions) {
    this.permissions.clear();
    this.permissions.addAll(newPermissions);
  }

  /** {@return an unmodifiable view of this role's permissions} */
  public Set<String> getPermissions() {
    return Set.copyOf(permissions);
  }
}
