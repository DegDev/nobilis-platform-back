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

import io.github.degdev.engine.common.notifications.NotificationsAutoConfiguration;
import io.github.degdev.engine.common.notifications.NotificationsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the notifications screens only when their store exists — same shape as {@code
 * ContentBlockWebAutoConfiguration}. Both controllers are registered as conditional {@code @Bean}s
 * gated on a {@link NotificationsService}; they are excluded from the host component scan (see
 * {@code AdminApplication}).
 */
@AutoConfiguration(after = NotificationsAutoConfiguration.class)
@ConditionalOnBean(NotificationsService.class)
public class NotificationsWebAutoConfiguration {

  @Bean
  public NotificationTypeController notificationTypeController(NotificationsService service) {
    return new NotificationTypeController(service);
  }

  @Bean
  public NotificationTemplateController notificationTemplateController(
      NotificationsService service) {
    return new NotificationTemplateController(service);
  }
}
