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

import java.math.BigDecimal;
import java.util.List;

/**
 * One catalog field's metadata, shaped for rendering a data-driven form — the read model {@link
 * AiProviderDefaults} produces from {@link AiProviderField}/{@link AiProviderFieldOption}. Carries
 * no current/saved value; that lives in {@code ResolvedAiProfile.params()} once a profile exists.
 *
 * @param fieldKey the field's stable key (e.g. {@code "temperature"})
 * @param category infra/operational/secret
 * @param type the rendered control type
 * @param editable whether the admin form allows editing this field
 * @param defaultValue the catalog default, as a string (parsed per {@code type})
 * @param minValue the inclusive lower bound, for {@code NUMBER} fields (nullable)
 * @param maxValue the inclusive upper bound, for {@code NUMBER} fields (nullable)
 * @param options the selectable values, for {@code SELECT}/{@code MULTISELECT} fields (empty
 *     otherwise)
 */
public record AiFieldDescriptor(
    String fieldKey,
    AiFieldCategory category,
    AiFieldType type,
    boolean editable,
    String defaultValue,
    BigDecimal minValue,
    BigDecimal maxValue,
    List<String> options) {}
