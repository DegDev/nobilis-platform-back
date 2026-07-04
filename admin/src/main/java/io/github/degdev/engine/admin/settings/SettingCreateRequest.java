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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body to create a setting. Bean-validated at the controller; a violation is reported as an
 * RFC 9457 {@code 400} with a {@code fieldErrors} array (see {@code GlobalExceptionHandler}). The
 * {@code value} is the PLAINTEXT — the service encrypts it before storage when {@code secret}.
 *
 * @param key the unique setting key (required, ≤ 255 chars)
 * @param value the plaintext value (optional, ≤ 4096 chars)
 * @param secret whether the value must be stored encrypted
 */
public record SettingCreateRequest(
    @NotBlank @Size(max = 255) String key, @Size(max = 4096) String value, boolean secret) {}
