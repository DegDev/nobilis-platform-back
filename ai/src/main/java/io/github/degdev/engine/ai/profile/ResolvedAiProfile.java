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

import java.util.Map;

/**
 * The resolved answer to "which provider serves this purpose, with which params" — {@link
 * AiProfileService#resolve(String)}'s return shape. When no {@link AiProfile} has been saved yet,
 * {@code providerCode} falls back to the purpose's catalog default provider and {@code params}
 * holds pure catalog defaults, so the mechanism works before any admin ever opens the form.
 *
 * <p>{@code params} carries only {@code INFRA}/{@code OPERATIONAL} field values (plain strings, as
 * stored/typed by the catalog); {@code SECRET} fields are deliberately excluded — their plaintext
 * only ever exists via {@link AiSecretStore}'s explicit, call-time read, never inside a value map
 * that could be logged, serialized, or otherwise leak.
 *
 * @param purpose the purpose this resolution is for
 * @param providerCode the provider to call
 * @param params the effective field-key/value map (catalog defaults overridden by saved params)
 */
public record ResolvedAiProfile(String purpose, String providerCode, Map<String, String> params) {}
