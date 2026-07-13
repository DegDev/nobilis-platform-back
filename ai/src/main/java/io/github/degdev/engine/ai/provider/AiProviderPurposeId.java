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
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite key of {@link AiProviderPurpose}: which provider serves a given purpose. */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProviderPurposeId implements Serializable {

  @Column(name = "purpose", length = 64)
  private String purpose;

  @Column(name = "provider_code", length = 64)
  private String providerCode;

  /**
   * Creates a purpose/provider key pair.
   *
   * @param purpose the purpose key (e.g. {@code "default"})
   * @param providerCode the provider's {@link AiProvider#getCode()}
   */
  public AiProviderPurposeId(String purpose, String providerCode) {
    this.purpose = purpose;
    this.providerCode = providerCode;
  }
}
