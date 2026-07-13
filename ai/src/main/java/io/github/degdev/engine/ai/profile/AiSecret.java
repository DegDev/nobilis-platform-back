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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One encrypted secret field's value, keyed by a composite string {@code ref} (conventionally
 * {@code "<purpose>.<providerCode>.<fieldKey>"}) rather than a foreign key to {@link AiProfile} — a
 * secret can be set before a profile row exists (e.g. a one-off health-check probe) and the ref
 * doubles as a human-auditable identifier. {@code value} always holds ciphertext; encryption via
 * the engine's existing {@code CryptoService} is wired in the next slice — this slice only shapes
 * the storage. No rows are seeded (Ollama needs no key).
 */
@Getter
@Entity
@Table(name = "ai_secret")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSecret {

  @Id
  @Column(name = "ref", length = 255)
  private String ref;

  @Column(name = "value", nullable = false)
  private String value;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Creates one secret entry.
   *
   * @param ref the composite {@code "<purpose>.<providerCode>.<fieldKey>"} key
   * @param value the encrypted value (ciphertext)
   * @param updatedAt when this value was last written
   */
  public AiSecret(String ref, String value, Instant updatedAt) {
    this.ref = ref;
    this.value = value;
    this.updatedAt = updatedAt;
  }
}
