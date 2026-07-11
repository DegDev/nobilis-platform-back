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

import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.common.notifications.NotificationTypeNotFoundException;
import io.github.degdev.engine.common.notifications.NotificationsService;
import io.github.degdev.engine.common.notifications.Transport;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification templates CRUD — same mounting shape as {@link NotificationTypeController}.
 * Templates are keyed by the pair {@code (typeKey, transport)}; translations are upserted/removed
 * per locale.
 */
@RestController
@RequestMapping("/admin/api/notification-templates")
@RequiresPermission(EnginePermissions.NOTIFICATIONS_MANAGE)
public class NotificationTemplateController {

  private final NotificationsService notificationsService;

  public NotificationTemplateController(NotificationsService notificationsService) {
    this.notificationsService = notificationsService;
  }

  @GetMapping
  public PagedModel<NotificationTemplateDto> list(
      @RequestParam(required = false) String typeKey, Pageable pageable) {
    return new PagedModel<>(
        (typeKey != null && !typeKey.isBlank()
                ? notificationsService.listTemplatesByType(typeKey, pageable)
                : notificationsService.listTemplates(pageable))
            .map(NotificationTemplateDto::from));
  }

  @GetMapping("/{typeKey}/{transport}")
  public NotificationTemplateDto get(
      @PathVariable String typeKey, @PathVariable Transport transport) {
    return notificationsService
        .findTemplate(typeKey, transport)
        .map(NotificationTemplateDto::from)
        .orElseThrow(
            () ->
                new NotificationTypeNotFoundException(
                    "Template for type '"
                        + typeKey
                        + "' + transport '"
                        + transport
                        + "' does not exist"));
  }

  @PostMapping
  public ResponseEntity<NotificationTemplateDto> create(
      @Valid @RequestBody NotificationTemplateCreateRequest request) {
    NotificationTemplateDto created =
        NotificationTemplateDto.from(
            notificationsService.createTemplate(request.typeKey(), request.transport()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{typeKey}/{transport}/translations/{locale}")
  public NotificationTemplateDto upsertTranslation(
      @PathVariable String typeKey,
      @PathVariable Transport transport,
      @PathVariable String locale,
      @Valid @RequestBody NotificationTemplateTranslationRequest request) {
    return NotificationTemplateDto.from(
        notificationsService.upsertTranslation(
            typeKey, transport, locale, request.subject(), request.body()));
  }

  @DeleteMapping("/{typeKey}/{transport}/translations/{locale}")
  public ResponseEntity<Void> removeTranslation(
      @PathVariable String typeKey,
      @PathVariable Transport transport,
      @PathVariable String locale) {
    notificationsService.removeTranslation(typeKey, transport, locale);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{typeKey}/{transport}")
  public ResponseEntity<Void> delete(
      @PathVariable String typeKey, @PathVariable Transport transport) {
    notificationsService.deleteTemplate(typeKey, transport);
    return ResponseEntity.noContent().build();
  }
}
