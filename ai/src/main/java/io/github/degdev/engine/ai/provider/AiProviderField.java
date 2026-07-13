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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One field of the data-driven form rendered for a {@code (purpose, provider)} pair — the catalog
 * that drives the admin AI-profile screen instead of a hardcoded form per provider. {@code
 * category} distinguishes read-only infra values from admin-editable operational params and
 * encrypted secrets (see {@link AiFieldCategory}); {@code type} drives the rendered control (see
 * {@link AiFieldType}). Static seed data this milestone — admin-editable catalog CRUD is a later
 * concern.
 */
@Getter
@Entity
@Table(name = "ai_provider_field")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProviderField extends BaseEntity {

  @Column(name = "purpose", nullable = false, length = 64)
  private String purpose;

  @Column(name = "provider_code", nullable = false, length = 64)
  private String providerCode;

  @Column(name = "field_key", nullable = false, length = 64)
  private String fieldKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private AiFieldCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private AiFieldType type;

  @Column(name = "editable", nullable = false)
  private boolean editable;

  @Column(name = "default_value", length = 1024)
  private String defaultValue;

  @Column(name = "min_value")
  private BigDecimal minValue;

  @Column(name = "max_value")
  private BigDecimal maxValue;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /**
   * Creates one catalog field descriptor.
   *
   * @param purpose the purpose this field applies to
   * @param providerCode the provider this field applies to
   * @param fieldKey the field's stable key (e.g. {@code "temperature"})
   * @param category infra/operational/secret
   * @param type the rendered control type
   * @param editable whether the admin form allows editing this field
   * @param defaultValue the catalog default, as a string (parsed per {@code type})
   * @param minValue the inclusive lower bound, for {@code NUMBER} fields (nullable)
   * @param maxValue the inclusive upper bound, for {@code NUMBER} fields (nullable)
   * @param sortOrder the display order among a provider's fields
   */
  public AiProviderField(
      String purpose,
      String providerCode,
      String fieldKey,
      AiFieldCategory category,
      AiFieldType type,
      boolean editable,
      String defaultValue,
      BigDecimal minValue,
      BigDecimal maxValue,
      int sortOrder) {
    this.purpose = purpose;
    this.providerCode = providerCode;
    this.fieldKey = fieldKey;
    this.category = category;
    this.type = type;
    this.editable = editable;
    this.defaultValue = defaultValue;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.sortOrder = sortOrder;
  }
}
