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
package io.github.degdev.engine.common.settings;

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
 * A single key/value engine setting.
 *
 * <p>When {@code secret} is {@code true} the stored {@code value} is a {@code v1:}-prefixed
 * ciphertext (see {@code CryptoService}); plaintext never touches the column. Non-secret values are
 * stored as-is. Reads/writes go through {@code SettingsService}, which owns the encrypt-on-write /
 * decrypt-on-read policy.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on the mutable fields, so the
 * natural key stays immutable after construction. Equality is by that business key alone ({@code
 * onlyExplicitlyIncluded}, {@code callSuper = false}) — never the generated id and never an
 * association — so it is stable across the entity lifecycle and cannot trigger lazy loading.
 * {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "setting")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting extends BaseEntity {

  @EqualsAndHashCode.Include
  @Column(name = "key", nullable = false, unique = true, length = 255)
  private String key;

  @Setter
  @Column(name = "value", length = 4096)
  private String value;

  @Setter
  @Column(name = "secret", nullable = false)
  private boolean secret;

  /**
   * Creates a new setting. The {@code value} must already be in its stored form ({@code
   * SettingsService} encrypts secrets before constructing) — this type does not encrypt.
   *
   * @param key the unique, immutable natural key
   * @param value the stored value (ciphertext when {@code secret}, plaintext otherwise)
   * @param secret whether {@code value} holds an encrypted secret
   */
  public Setting(String key, String value, boolean secret) {
    this.key = key;
    this.value = value;
    this.secret = secret;
  }
}
