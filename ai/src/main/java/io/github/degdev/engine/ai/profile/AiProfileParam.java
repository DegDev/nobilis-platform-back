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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One operational field's saved value on a profile, overriding the catalog default. A pure value
 * table — composite PK {@code (profile_id, field_key)}, no surrogate id, per the join-table
 * convention; not audited, since the owning {@link AiProfile} already carries the audit trail.
 * Modelled as a genuine {@code @Entity} (rather than an owner-side {@code @ElementCollection}) so a
 * later slice can bulk delete-then-insert a profile's params independently, per the pattern
 * source's save flow (full replace on every save).
 */
@Getter
@Entity
@Table(name = "ai_profile_param")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiProfileParam {

  @EmbeddedId private AiProfileParamId id;

  @Column(name = "value")
  private String value;

  /**
   * Creates one saved param value.
   *
   * @param id the composite profile/field-key key
   * @param value the saved value, as a string (parsed per the field's {@code type})
   */
  public AiProfileParam(AiProfileParamId id, String value) {
    this.id = id;
    this.value = value;
  }
}
