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
package io.github.degdev.engine.admin.ai;

import java.util.Map;

/**
 * The API view of a resolved AI profile.
 *
 * <p><b>Secret masking.</b> {@code params} already excludes {@code SECRET}-category fields (see
 * {@code ResolvedAiProfile}) — a secret's plaintext or ciphertext is never produced to build this
 * DTO. {@code secretsSet} tells the form which secret fields already have a value stored, WITHOUT
 * revealing it (mirrors {@code SettingDto}'s {@code value = null}-on-secret masking, adapted to a
 * per-field map since a profile can have several secret fields).
 *
 * @param purpose the purpose this profile is for
 * @param providerCode the resolved provider
 * @param params the effective non-secret field values (catalog defaults merged with saved
 *     overrides)
 * @param secretsSet for each {@code SECRET}-category field in the provider's descriptor, whether a
 *     value is currently stored (never the value itself)
 */
public record AiProfileDto(
    String purpose,
    String providerCode,
    Map<String, String> params,
    Map<String, Boolean> secretsSet) {}
