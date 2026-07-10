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
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generic, keyed piece of CMS content — an engine mechanism, not a typed per-domain entity.
 * Publication is per-item ({@link ContentStatus}), not per-locale; the per-locale bodies live in
 * {@link #translations}.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on {@code status}, so the natural
 * key stays immutable after construction. Equality is by that business key alone ({@code
 * onlyExplicitlyIncluded}, {@code callSuper = false}), mirroring {@code Setting}.
 * {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "content_block")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentBlock extends BaseEntity {

  @EqualsAndHashCode.Include
  @Column(name = "key", nullable = false, unique = true, length = 255)
  private String key;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContentStatus status;

  @OneToMany(mappedBy = "contentBlock", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ContentTranslation> translations = new ArrayList<>();

  /**
   * Creates a new content block with no translations yet.
   *
   * @param key the unique, immutable natural key
   * @param status the initial publication status
   */
  public ContentBlock(String key, ContentStatus status) {
    this.key = key;
    this.status = status;
  }

  /**
   * Adds a translation, keeping both sides of the association in sync.
   *
   * @param translation the translation to attach to this block
   */
  public void addTranslation(ContentTranslation translation) {
    translation.assignContentBlock(this);
    translations.add(translation);
  }
}
