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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of {@link Role}s: list/get existing rows, create, update (name + permissions —
 * the {@code code} business key is immutable), and delete. Enforces the role invariants and leaves
 * HTTP mapping to the admin layer, signalling failures with plain domain exceptions ({@link
 * RoleConflictException}, {@link UnknownPermissionException}) and {@link Optional}/{@code boolean}
 * for absence — it never depends on any web type.
 *
 * <p>Account↔role linking is NOT here (a later pass): this service manages the role catalog itself.
 * Delete is guarded against the {@code account_role} foreign key (which is {@code NO ACTION}) by a
 * reference count, so a role still held by an account is refused, not left to a raw DB error.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * RoleServiceAutoConfiguration}, gated on JPA being active, so a stateless host simply has no role
 * service. Lombok {@code @RequiredArgsConstructor} wires the {@code final} repository;
 * {@code @Slf4j} provides {@code log}.
 */
@Slf4j
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository repository;

  /**
   * Lists roles, one page at a time.
   *
   * @param pageable the page request (page number, size, sort)
   * @return the requested page of roles, each with its permissions loaded
   */
  @Transactional(readOnly = true)
  public Page<Role> list(Pageable pageable) {
    return repository.findAll(pageable).map(RoleService::withPermissionsLoaded);
  }

  /**
   * Reads one role by id.
   *
   * @param id the role id
   * @return the role (with its permissions loaded), or empty if none has that id
   */
  @Transactional(readOnly = true)
  public Optional<Role> find(Long id) {
    return repository.findById(id).map(RoleService::withPermissionsLoaded);
  }

  /**
   * Creates a role from a code, a label and a set of permissions.
   *
   * @param code the unique, immutable business key
   * @param name the human-readable label
   * @param permissions the permission values to grant (each must be an {@link EnginePermissions}
   *     value; {@code null} is treated as none)
   * @return the persisted role
   * @throws UnknownPermissionException if any permission is not defined by the engine
   * @throws RoleConflictException if a role already exists with the same code
   */
  @Transactional
  public Role create(String code, String name, Set<String> permissions) {
    Set<String> perms = validated(permissions);
    if (repository.findByCode(code).isPresent()) {
      throw new RoleConflictException("error.role-code-exists", code);
    }
    Role role = new Role(code, name);
    perms.forEach(role::addPermission);
    log.debug("Creating role '{}' with {} permission(s)", code, perms.size());
    return repository.save(role);
  }

  /**
   * Updates the label and permissions of an existing role. The {@code code} is immutable and never
   * changes.
   *
   * @param id the role id
   * @param name the new label
   * @param permissions the new, complete set of permission values ({@code null} clears them)
   * @return the updated role, or empty if no role has that id
   * @throws UnknownPermissionException if any permission is not defined by the engine
   */
  @Transactional
  public Optional<Role> update(Long id, String name, Set<String> permissions) {
    return repository
        .findById(id)
        .map(
            role -> {
              Set<String> perms = validated(permissions);
              role.setName(name);
              role.replacePermissions(perms);
              log.debug("Updated role '{}' ({} permission(s))", role.getCode(), perms.size());
              return repository.save(role);
            });
  }

  /**
   * Deletes a role, refusing when it is still assigned to any account (the {@code account_role}
   * foreign key is {@code NO ACTION}, so a raw delete would fail the constraint).
   *
   * @param id the role id
   * @return {@code true} if a role was deleted, {@code false} if no role has that id
   * @throws RoleConflictException if the role is still assigned to one or more accounts
   */
  @Transactional
  public boolean delete(Long id) {
    return repository
        .findById(id)
        .map(
            role -> {
              long assigned = repository.countAssignedAccounts(role);
              if (assigned > 0) {
                throw new RoleConflictException(
                    "error.role-assigned-to-accounts", role.getCode(), assigned);
              }
              repository.delete(role);
              log.debug("Deleted role '{}'", role.getCode());
              return true;
            })
        .orElse(false);
  }

  /** Normalizes a possibly-null permission set and rejects any value the engine does not define. */
  private static Set<String> validated(Set<String> permissions) {
    Set<String> perms = permissions == null ? Set.of() : permissions;
    Set<String> unknown = new LinkedHashSet<>(perms);
    unknown.removeAll(EnginePermissions.ALL);
    if (!unknown.isEmpty()) {
      throw new UnknownPermissionException(unknown);
    }
    return perms;
  }

  /**
   * Forces the lazy {@code permissions} {@code @ElementCollection} to load inside the transaction
   * so the controller can map it after the entity detaches (open-in-view is off).
   */
  private static Role withPermissionsLoaded(Role role) {
    role.getPermissions().size();
    return role;
  }
}
