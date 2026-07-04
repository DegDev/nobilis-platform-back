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
 * Request body to update a role. The {@code code} is the immutable business key and is NOT part of
 * an update — only the label and the permission set change. Bean-validated at the controller (RFC
 * 9457 {@code 400} with {@code fieldErrors} on violation).
 *
 * @param name the new label (required, ≤ 255 chars)
 * @param permissions the new, complete set of permission values (may be empty/omitted to clear)
 */
public record RoleUpdateRequest(@NotBlank @Size(max = 255) String name, Set<String> permissions) {}
