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
package io.github.degdev.engine.admin.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body to create a notification type.
 *
 * @param key the unique notification type key (required, <= 255 chars)
 * @param enabled whether the type starts active (required)
 * @param description an optional human-readable note (<= 1024 chars)
 */
public record NotificationTypeCreateRequest(
    @NotBlank @Size(max = 255) String key,
    @NotNull Boolean enabled,
    @Size(max = 1024) String description) {}
