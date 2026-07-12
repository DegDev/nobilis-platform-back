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
package io.github.degdev.engine.ai.profile;

import io.github.degdev.engine.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The admin-saved binding of a purpose to the one provider that serves it: "when {@code purpose}
 * needs an LLM call, use {@code providerCode}." {@code purpose} is UNIQUE — one active profile per
 * purpose, enforced by the DB, not a separate "purpose" entity (purpose stays a plain string key
 * shared across {@link io.github.degdev.engine.ai.provider.AiProviderField#getPurpose()} and {@link
 * io.github.degdev.engine.ai.provider.AiProviderPurposeId#getPurpose()}).
 *
 * <p>{@code category} is carried over from the pattern source's resolved-profile shape; this slice
 * only creates the column — its exact semantics are pinned down when {@code AiProfileService} lands
 * in the next slice, so it stays a nullable, otherwise-unused string for now.
 */
@Getter
@Entity
@Table(name = "ai_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProfile extends BaseEntity {

  @Column(name = "purpose", nullable = false, unique = true, length = 64)
  private String purpose;

  @Column(name = "category", length = 64)
  private String category;

  @Column(name = "provider_code", nullable = false, length = 64)
  private String providerCode;

  /**
   * Creates a profile binding a purpose to a provider.
   *
   * @param purpose the unique purpose key
   * @param category see the class-level note — not yet load-bearing
   * @param providerCode the chosen provider's code
   */
  public AiProfile(String purpose, String category, String providerCode) {
    this.purpose = purpose;
    this.category = category;
    this.providerCode = providerCode;
  }
}
