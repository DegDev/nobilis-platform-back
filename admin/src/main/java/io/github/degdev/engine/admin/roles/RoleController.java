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

import io.github.degdev.engine.admin.api.NotFoundException;
import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.role.RoleService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles CRUD — the second screen on the admin REST framework, following the {@code
 * SettingsController} shape: RFC 9457 errors ({@code GlobalExceptionHandler}), {@code
 * Pageable}/{@code PagedModel} pagination, and {@link RequiresPermission} at the class level so
 * every handler needs {@link EnginePermissions#ACCOUNT_MANAGE} (the one permission that gates both
 * the accounts and roles screens — there is no separate {@code ROLE_MANAGE}).
 *
 * <p>{@code code} is the immutable business key: it is set at {@code POST} and never changes, so
 * {@code PUT} carries only the label and permissions. {@code DELETE} is {@code 404} when the id is
 * unknown and {@code 409} when the role is still assigned to accounts (the {@code account_role}
 * foreign key is {@code NO ACTION}). {@code GET /permissions} returns the engine's assignable
 * permission catalog. The {@code {id}} paths are constrained to digits so {@code /permissions}
 * never collides with {@code /{id}}.
 *
 * <p><b>Mounted only with a store.</b> Like {@code SettingsController} this is a
 * {@code @RestController} EXCLUDED from the host component scan (see {@code AdminApplication}) and
 * registered as a {@code @Bean} by {@code RoleAdminAutoConfiguration}, gated on a {@link
 * RoleService} existing — i.e. only when the database is active. The stateless default host mounts
 * no roles endpoints.
 */
@RestController
@RequestMapping("/admin/api/roles")
@RequiresPermission(EnginePermissions.ACCOUNT_MANAGE)
public class RoleController {

  private final RoleService roleService;

  /**
   * Creates the controller.
   *
   * @param roleService the role management service
   */
  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  /**
   * Lists roles, one page at a time.
   *
   * @param pageable the page request ({@code ?page=&size=&sort=})
   * @return a page of roles as {@link RoleDto}s
   */
  @GetMapping
  public PagedModel<RoleDto> list(Pageable pageable) {
    return new PagedModel<>(roleService.list(pageable).map(RoleDto::from));
  }

  /**
   * Returns the engine's catalog of assignable permissions.
   *
   * @return every engine permission value
   */
  @GetMapping("/permissions")
  public Set<String> permissions() {
    return EnginePermissions.ALL;
  }

  /**
   * Reads one role by id.
   *
   * @param id the role id
   * @return the role as a {@link RoleDto}
   * @throws NotFoundException if no role has that id
   */
  @GetMapping("/{id:\\d+}")
  public RoleDto get(@PathVariable Long id) {
    return roleService
        .find(id)
        .map(RoleDto::from)
        .orElseThrow(() -> new NotFoundException("No role with id " + id));
  }

  /**
   * Creates a role.
   *
   * @param request the validated create request
   * @return {@code 201} with the created role
   */
  @PostMapping
  public ResponseEntity<RoleDto> create(@Valid @RequestBody RoleCreateRequest request) {
    RoleDto created =
        RoleDto.from(roleService.create(request.code(), request.name(), request.permissions()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Updates a role's label and permissions (the {@code code} is immutable).
   *
   * @param id the role id
   * @param request the validated update request
   * @return the updated role
   * @throws NotFoundException if no role has that id
   */
  @PutMapping("/{id:\\d+}")
  public RoleDto update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
    return roleService
        .update(id, request.name(), request.permissions())
        .map(RoleDto::from)
        .orElseThrow(() -> new NotFoundException("No role with id " + id));
  }

  /**
   * Deletes a role.
   *
   * @param id the role id
   * @return {@code 204} when deleted
   * @throws NotFoundException if no role has that id
   */
  @DeleteMapping("/{id:\\d+}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!roleService.delete(id)) {
      throw new NotFoundException("No role with id " + id);
    }
    return ResponseEntity.noContent().build();
  }
}
