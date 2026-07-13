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
package io.github.degdev.engine.admin.settings;

import io.github.degdev.engine.admin.api.NobilisAdminController;
import io.github.degdev.engine.admin.api.NotFoundException;
import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.common.settings.SettingsService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Engine settings CRUD — the first screen built on the admin REST framework and the reference shape
 * every engine CRUD screen follows: RFC 9457 errors ({@code GlobalExceptionHandler}), {@code
 * Pageable}/{@code PagedModel} pagination, and {@link RequiresPermission} at the class level so
 * every handler needs {@link EnginePermissions#SETTINGS_MANAGE} (the MVC-layer permission check, on
 * top of the servlet-layer contour that already fenced off anonymous/non-admin callers).
 *
 * <p>Reads go through {@code SettingsService.find}/{@code list} (never {@code get}), so a secret's
 * ciphertext is never decrypted merely to display it, and {@link SettingDto} masks the value to
 * {@code null}. Writes take PLAINTEXT; the service encrypts secrets before storage. {@code PUT}
 * upserts at the given key (create-or-replace, idempotent); {@code DELETE} is {@code 404} when the
 * key is unset.
 *
 * <p><b>Mounted only with a store.</b> This is a {@code @RestController} (so its handler mappings
 * are detected — a type-level {@code @RequestMapping} without {@code @Controller} is not) but it is
 * EXCLUDED from the host's component scan (see {@code AdminApplication}) and instead registered as
 * a {@code @Bean} by {@code SettingsWebAutoConfiguration}, gated on a {@link SettingsService}
 * existing — i.e. only when the database is active. The stateless default admin host has no
 * settings store, so the screen is not mounted there; excluding it from the scan keeps that
 * conditional {@code @Bean} the single registration (no ambiguous-mapping clash with a scanned
 * copy).
 */
@NobilisAdminController(permission = EnginePermissions.SETTINGS_MANAGE)
@RequestMapping("${nobilis.api.v1.url:/api}/admin/settings")
public class SettingsController {

  private final SettingsService settingsService;

  /**
   * Creates the controller.
   *
   * @param settingsService the settings store
   */
  public SettingsController(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  /**
   * Lists settings, one page at a time. Secret values are masked.
   *
   * @param keyPrefix when given, only keys starting with this prefix are returned (e.g. {@code
   *     integration.} for the admin Integrations screen)
   * @param pageable the page request ({@code ?page=&size=&sort=})
   * @return a page of settings as {@link SettingDto}s
   */
  @GetMapping
  public PagedModel<SettingDto> list(
      @RequestParam(required = false) String keyPrefix, Pageable pageable) {
    return new PagedModel<>(settingsService.list(keyPrefix, pageable).map(SettingDto::from));
  }

  /**
   * Reads one setting by key. Secret values are masked.
   *
   * @param key the setting key
   * @return the setting as a {@link SettingDto}
   * @throws NotFoundException if no setting has that key
   */
  @GetMapping("/{key}")
  public SettingDto get(@PathVariable String key) {
    return settingsService
        .find(key)
        .map(SettingDto::from)
        .orElseThrow(() -> new NotFoundException("error.setting-not-found", key));
  }

  /**
   * Creates a setting (or replaces one at the same key). Encrypts the value first when {@code
   * secret}.
   *
   * @param request the validated create request (plaintext value)
   * @return {@code 201} with the created setting (masked if secret)
   */
  @PostMapping
  public ResponseEntity<SettingDto> create(@Valid @RequestBody SettingCreateRequest request) {
    SettingDto created =
        SettingDto.from(settingsService.set(request.key(), request.value(), request.secret()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Updates the setting at {@code key} (creating it if absent — {@code PUT} is idempotent).
   *
   * @param key the setting key
   * @param request the validated update request (plaintext value)
   * @return the stored setting (masked if secret)
   */
  @PutMapping("/{key}")
  public SettingDto update(
      @PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) {
    return SettingDto.from(settingsService.set(key, request.value(), request.secret()));
  }

  /**
   * Deletes the setting at {@code key}.
   *
   * @param key the setting key
   * @return {@code 204} when deleted
   * @throws NotFoundException if no setting has that key
   */
  @DeleteMapping("/{key}")
  public ResponseEntity<Void> delete(@PathVariable String key) {
    if (!settingsService.delete(key)) {
      throw new NotFoundException("error.setting-not-found", key);
    }
    return ResponseEntity.noContent().build();
  }
}
