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
package io.github.degdev.engine.admin.notifications;

import io.github.degdev.engine.admin.api.NotFoundException;
import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.common.notifications.NotificationTypeNotFoundException;
import io.github.degdev.engine.common.notifications.NotificationsService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification types CRUD — follows the {@code SettingsController}/{@code ContentBlockController}
 * shape: RFC 9457 errors, {@code Pageable}/{@code PagedModel}, class-level {@link
 * RequiresPermission} ({@link EnginePermissions#NOTIFICATIONS_MANAGE}).
 *
 * <p>Mounted only with a store: excluded from component scan, registered as a conditional
 * {@code @Bean} by {@link NotificationsWebAutoConfiguration}.
 */
@RestController
@RequestMapping("/admin/api/notification-types")
@RequiresPermission(EnginePermissions.NOTIFICATIONS_MANAGE)
public class NotificationTypeController {

  private final NotificationsService notificationsService;

  public NotificationTypeController(NotificationsService notificationsService) {
    this.notificationsService = notificationsService;
  }

  @GetMapping
  public PagedModel<NotificationTypeDto> list(Pageable pageable) {
    return new PagedModel<>(
        notificationsService.listTypes(pageable).map(NotificationTypeDto::from));
  }

  @GetMapping("/{key}")
  public NotificationTypeDto get(@PathVariable String key) {
    return notificationsService
        .findType(key)
        .map(NotificationTypeDto::from)
        .orElseThrow(
            () -> new NotificationTypeNotFoundException("error.notification-type-not-found", key));
  }

  @PostMapping
  public ResponseEntity<NotificationTypeDto> create(
      @Valid @RequestBody NotificationTypeCreateRequest request) {
    NotificationTypeDto created =
        NotificationTypeDto.from(
            notificationsService.createType(
                request.key(), request.enabled(), request.description()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{key}")
  public NotificationTypeDto update(
      @PathVariable String key, @Valid @RequestBody NotificationTypeUpdateRequest request) {
    return NotificationTypeDto.from(
        notificationsService.updateType(key, request.enabled(), request.description()));
  }

  @DeleteMapping("/{key}")
  public ResponseEntity<Void> delete(@PathVariable String key) {
    if (!notificationsService.deleteType(key)) {
      throw new NotFoundException("error.notification-type-not-found", key);
    }
    return ResponseEntity.noContent().build();
  }
}
