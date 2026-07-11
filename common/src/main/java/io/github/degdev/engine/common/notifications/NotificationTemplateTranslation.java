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
package io.github.degdev.engine.common.notifications;

import io.github.degdev.engine.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single locale's subject/body for a {@link NotificationTemplate}. A full entity (own id/audit
 * trail), not an {@code @ElementCollection}, so each translation can be tracked independently —
 * mirroring {@code ContentTranslation}.
 *
 * <p>{@code subject} is nullable (Telegram/SMS transports ignore it); {@code body} is always
 * required. Lombok: {@code @Getter} on the type; {@code @Setter} only on the mutable fields ({@code
 * subject}, {@code body}). The owning {@link NotificationTemplate} side of the association is
 * assigned only via {@link #assignTemplate}, called from {@link
 * NotificationTemplate#addTranslation}, so both sides stay in sync. Equality is by the business key
 * {@code (template, locale)}, mirroring the {@code Setting}/{@code ContentBlock} convention.
 * {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "notification_template_translation")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplateTranslation extends BaseEntity {

  @EqualsAndHashCode.Include
  @ManyToOne
  @JoinColumn(name = "template_id", nullable = false)
  private NotificationTemplate template;

  @EqualsAndHashCode.Include
  @Column(name = "locale", nullable = false, length = 10)
  private String locale;

  @Setter
  @Column(name = "subject", length = 512)
  private String subject;

  @Setter
  @Column(name = "body", nullable = false)
  private String body;

  /**
   * Creates a new translation. Not yet attached to a {@link NotificationTemplate} — use {@link
   * NotificationTemplate#addTranslation} to attach it.
   *
   * @param locale the locale this translation is written in
   * @param subject the localized subject (may be {@code null} for body-only transports)
   * @param body the localized body
   */
  public NotificationTemplateTranslation(String locale, String subject, String body) {
    this.locale = locale;
    this.subject = subject;
    this.body = body;
  }

  /**
   * Assigns the owning {@link NotificationTemplate} side of the association. Package-private: only
   * {@link NotificationTemplate#addTranslation} may call this, so both sides stay in sync.
   *
   * @param template the template this translation belongs to
   */
  void assignTemplate(NotificationTemplate template) {
    this.template = template;
  }
}
