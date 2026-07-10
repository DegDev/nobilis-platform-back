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
package io.github.degdev.engine.admin.cms;

import io.github.degdev.engine.common.cms.ContentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body to change a content block's publication status.
 *
 * @param status the new publication status (required)
 */
public record ContentBlockStatusUpdateRequest(@NotNull ContentStatus status) {}
