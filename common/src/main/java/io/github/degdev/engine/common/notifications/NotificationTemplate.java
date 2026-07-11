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
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A localized notification template for one {@link NotificationType} on one {@link Transport}.
 *
 * <p>The template is uniquely identified by the pair {@code (type, transport)} — one template per
 * type per channel (e.g. an {@code order.created} type may have an {@code EMAIL} template and a
 * {@code TELEGRAM} template). The actual per-locale subject/body pairs live in the {@link
 * NotificationTemplateTranslation} child collection, mirroring the CMS {@code ContentBlock}/{@code
 * ContentTranslation} parent-child pattern.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on {@code transport}, so the
 * business-key pair stays immutable after construction. Equality is by that business key alone
 * ({@code onlyExplicitlyIncluded}, {@code callSuper = false}), mirroring {@code Setting}/{@code
 * ContentBlock}/{@code ContentTranslation}. {@code @NoArgsConstructor(PROTECTED)} satisfies
 * Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(
    name = "notification_template",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_notification_template_type_transport",
            columnNames = {"type_id", "transport"}))
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate extends BaseEntity {

  @EqualsAndHashCode.Include
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "type_id", nullable = false)
  private NotificationType type;

  @EqualsAndHashCode.Include
  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "transport", nullable = false, length = 20)
  private Transport transport;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<NotificationTemplateTranslation> translations = new ArrayList<>();

  public NotificationTemplate(NotificationType type, Transport transport) {
    this.type = type;
    this.transport = transport;
  }

  public void addTranslation(NotificationTemplateTranslation translation) {
    translation.assignTemplate(this);
    translations.add(translation);
  }
}
