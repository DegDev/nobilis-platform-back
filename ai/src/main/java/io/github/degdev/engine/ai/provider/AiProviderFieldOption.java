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

import io.github.degdev.engine.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One selectable value for a {@code SELECT}/{@code MULTISELECT} {@link AiProviderField}. Named
 * {@code ai_provider_field_option} (not {@code ai_field_option}) so it sorts adjacent to {@code
 * ai_provider_field} in a schema listing, per the {@code entity_extra_fields} table-naming
 * convention. No provider has {@code SELECT}/{@code MULTISELECT} fields this milestone (Ollama's
 * catalog is all string/number), so no rows are seeded yet.
 */
@Getter
@Entity
@Table(name = "ai_provider_field_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProviderFieldOption extends BaseEntity {

  @Column(name = "field_id", nullable = false)
  private Long fieldId;

  @Column(name = "value", nullable = false, length = 255)
  private String value;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /**
   * Creates one selectable option.
   *
   * @param fieldId the owning {@link AiProviderField#getId()}
   * @param value the option's value
   * @param sortOrder the display order among the field's options
   */
  public AiProviderFieldOption(Long fieldId, String value, int sortOrder) {
    this.fieldId = fieldId;
    this.value = value;
    this.sortOrder = sortOrder;
  }
}
