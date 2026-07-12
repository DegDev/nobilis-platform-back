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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Links a purpose to the providers that can serve it. A pure membership join — composite PK {@code
 * (purpose, provider_code)}, no surrogate id, per the engine's join-table convention (the same
 * shape as {@code account_realm}/{@code role_permission}), modelled here as a genuine
 * {@code @Entity} (rather than an owner-side {@code @ElementCollection}) because a later slice
 * needs to query providers by purpose independently of loading the owning {@link AiProvider}.
 */
@Getter
@Entity
@Table(name = "ai_provider_purpose")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProviderPurpose {

  @EmbeddedId private AiProviderPurposeId id;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /**
   * Links a purpose to a provider.
   *
   * @param id the composite purpose/provider key
   * @param sortOrder the display order among providers offered for this purpose
   */
  public AiProviderPurpose(AiProviderPurposeId id, int sortOrder) {
    this.id = id;
    this.sortOrder = sortOrder;
  }
}
