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
package io.github.degdev.engine.admin.settings;

import io.github.degdev.engine.common.settings.Setting;

/**
 * The API view of a {@link Setting}.
 *
 * <p><b>Secret masking.</b> A secret's value is NEVER returned on a read path: {@link #from} emits
 * {@code value = null} when {@code secret} is true. The choice is to OMIT the value (null), not to
 * substitute a {@code "••••"} placeholder, so a client can tell "unset" from "hidden secret" only
 * by the {@code secret} flag and is never handed a fake value to round-trip. Decryption stays at
 * the point of use ({@code SettingsService.get}); it never happens to build this DTO, so a secret's
 * plaintext is not even produced on a list/get.
 *
 * @param key the setting key
 * @param value the plaintext value for a non-secret setting; {@code null} for a secret
 * @param secret whether this setting holds an encrypted secret
 */
public record SettingDto(String key, String value, boolean secret) {

  /**
   * Projects an entity to its API view, masking a secret's value to {@code null}.
   *
   * @param setting the stored setting
   * @return the masked API view
   */
  public static SettingDto from(Setting setting) {
    return new SettingDto(
        setting.getKey(), setting.isSecret() ? null : setting.getValue(), setting.isSecret());
  }
}
