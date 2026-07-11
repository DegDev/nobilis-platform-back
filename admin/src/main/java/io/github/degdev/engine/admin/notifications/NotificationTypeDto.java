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

/**
 * The API view of a {@link io.github.degdev.engine.common.notifications.NotificationType}.
 *
 * @param key the unique, immutable natural key
 * @param enabled whether the dispatcher should consider this type active
 * @param description an optional human-readable note (may be {@code null})
 */
public record NotificationTypeDto(String key, boolean enabled, String description) {

  public static NotificationTypeDto from(
      io.github.degdev.engine.common.notifications.NotificationType type) {
    return new NotificationTypeDto(type.getKey(), type.isEnabled(), type.getDescription());
  }
}
