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

import io.github.degdev.engine.common.notifications.NotificationTemplate;
import io.github.degdev.engine.common.notifications.NotificationTemplateTranslation;
import io.github.degdev.engine.common.notifications.Transport;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The API view of a {@link NotificationTemplate}: its type key, transport, and per-locale
 * subject/body pairs keyed by locale.
 *
 * @param typeKey the owning notification type's key
 * @param transport the transport channel
 * @param translations per-locale {subject, body} pairs, keyed by locale code
 */
public record NotificationTemplateDto(
    String typeKey, Transport transport, Map<String, TranslationDto> translations) {

  /** A single locale's subject + body. */
  public record TranslationDto(String subject, String body) {}

  public static NotificationTemplateDto from(NotificationTemplate template) {
    return new NotificationTemplateDto(
        template.getType().getKey(),
        template.getTransport(),
        template.getTranslations().stream()
            .collect(
                Collectors.toMap(
                    NotificationTemplateTranslation::getLocale,
                    t -> new TranslationDto(t.getSubject(), t.getBody()))));
  }
}
