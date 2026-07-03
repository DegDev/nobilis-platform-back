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
package io.github.degdev.engine.admin.roles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Request body to create a role. Bean-validated at the controller; a violation is reported as an
 * RFC 9457 {@code 400} with a {@code fieldErrors} array (see {@code GlobalExceptionHandler}). Each
 * permission must be an engine-defined value — the service rejects unknown permissions with a
 * {@code 400} (a separate check from these field constraints).
 *
 * @param code the unique, immutable business key (required, ≤ 64 chars)
 * @param name the human-readable label (required, ≤ 255 chars)
 * @param permissions the permission values to grant (may be empty/omitted)
 */
public record RoleCreateRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 255) String name,
    Set<String> permissions) {}
