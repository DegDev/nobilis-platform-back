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
package io.github.degdev.engine.ai.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A catalog entry for one LLM provider (e.g. Ollama). Admin-editable only via migration this
 * milestone — the catalog is static seed data, not a CRUD screen.
 *
 * <p>Unlike most engine entities, {@code code} IS the primary key rather than a {@link
 * io.github.degdev.engine.common.persistence.BaseEntity} surrogate id: the catalog's stable
 * business key already doubles as a natural, immutable identity, and every other {@code ai_*} table
 * references providers by this varchar code (never a Postgres enum, per convention). No audit
 * columns — this is a static seed table, not an admin-mutated record.
 */
@Getter
@Entity
@Table(name = "ai_provider")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProvider {

  @Id
  @Column(name = "code", length = 64)
  private String code;

  @Column(name = "label", nullable = false, length = 255)
  private String label;

  @Column(name = "hint", length = 1024)
  private String hint;

  @Column(name = "requires_local", nullable = false)
  private boolean requiresLocal;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /**
   * Creates a catalog entry for one provider.
   *
   * @param code the stable, unique provider code (e.g. {@code "ollama"})
   * @param label the human-readable label
   * @param hint an optional short description shown in the admin form
   * @param requiresLocal whether this provider needs a locally-reachable runtime (e.g. Ollama)
   * @param sortOrder the display order among providers
   */
  public AiProvider(String code, String label, String hint, boolean requiresLocal, int sortOrder) {
    this.code = code;
    this.label = label;
    this.hint = hint;
    this.requiresLocal = requiresLocal;
    this.sortOrder = sortOrder;
  }
}
