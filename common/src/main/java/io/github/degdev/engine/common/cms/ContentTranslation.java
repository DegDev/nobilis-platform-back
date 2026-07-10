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
package io.github.degdev.engine.common.cms;

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
 * A single locale's body for a {@link ContentBlock}. A full entity (own id/audit trail), not an
 * {@code @ElementCollection}, so each translation can be tracked independently.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on {@code body}. The owning {@link
 * ContentBlock} side of the association is assigned only via {@link #assignContentBlock}, called
 * from {@link ContentBlock#addTranslation}, so both sides stay in sync. Equality is by the business
 * key {@code (contentBlock, locale)}, mirroring the {@code Setting}/{@code ContentBlock}
 * convention. {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public
 * API.
 */
@Getter
@Entity
@Table(name = "content_translation")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTranslation extends BaseEntity {

  @EqualsAndHashCode.Include
  @ManyToOne
  @JoinColumn(name = "content_block_id", nullable = false)
  private ContentBlock contentBlock;

  @EqualsAndHashCode.Include
  @Column(name = "locale", nullable = false, length = 10)
  private String locale;

  @Setter
  @Column(name = "body", nullable = false)
  private String body;

  /**
   * Creates a new translation. Not yet attached to a {@link ContentBlock} — use {@link
   * ContentBlock#addTranslation} to attach it.
   *
   * @param locale the locale this body is written in
   * @param body the translated content
   */
  public ContentTranslation(String locale, String body) {
    this.locale = locale;
    this.body = body;
  }

  /**
   * Assigns the owning {@link ContentBlock} side of the association. Package-private: only {@link
   * ContentBlock#addTranslation} may call this, so both sides stay in sync.
   *
   * @param contentBlock the block this translation belongs to
   */
  void assignContentBlock(ContentBlock contentBlock) {
    this.contentBlock = contentBlock;
  }
}
