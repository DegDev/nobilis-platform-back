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

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request body to save an AI profile. Bean-validated at the controller; a violation is reported as
 * an RFC 9457 {@code 400} with a {@code fieldErrors} array (see {@code GlobalExceptionHandler}).
 * {@code params}/{@code secrets} are validated against the catalog by the service (unknown field,
 * out-of-bounds number, a secret field submitted as a plain param) — a separate check from these
 * field constraints, also surfaced as a {@code 400}.
 *
 * @param purpose the purpose to save a profile for (required)
 * @param provider the chosen provider's code (required)
 * @param params non-secret field values (may be empty/omitted)
 * @param secrets plaintext secret field values, keyed by field key (may be empty/omitted) — the
 *     controller encrypts these via {@code AiSecretStore}; a blank value is a no-op (existing
 *     secret left untouched, never cleared by omission)
 */
public record AiProfileSaveRequest(
    @NotBlank String purpose,
    @NotBlank String provider,
    Map<String, String> params,
    Map<String, String> secrets) {}
