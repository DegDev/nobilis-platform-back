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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A notification event kind — an engine mechanism, not a typed per-domain entity.
 *
 * <p>The {@code key} is free-form (like {@code Setting.key} / {@code ContentBlock.key}); the engine
 * ships no seeded event kinds. Domain products (milestone 07) insert their own rows ({@code
 * order.created}, {@code claim.assigned}, …). {@code enabled} gates whether the milestone-04
 * dispatcher considers the type active; {@code description} is an optional human-readable note.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on the mutable fields ({@code
 * enabled}, {@code description}), so the natural key stays immutable after construction. Equality
 * is by that business key alone ({@code onlyExplicitlyIncluded}, {@code callSuper = false}),
 * mirroring {@code Setting}/{@code ContentBlock}. {@code @NoArgsConstructor(PROTECTED)} satisfies
 * Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "notification_type")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationType extends BaseEntity {

  @EqualsAndHashCode.Include
  @Column(name = "key", nullable = false, unique = true, length = 255)
  private String key;

  @Setter
  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Setter
  @Column(name = "description", length = 1024)
  private String description;

  /**
   * Creates a new notification type.
   *
   * @param key the unique, immutable natural key
   * @param enabled whether the dispatcher should consider this type active
   * @param description an optional human-readable note (may be {@code null})
   */
  public NotificationType(String key, boolean enabled, String description) {
    this.key = key;
    this.enabled = enabled;
    this.description = description;
  }
}
