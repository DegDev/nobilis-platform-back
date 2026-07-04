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
package io.github.degdev.engine.admin.accounts;

import io.github.degdev.engine.auth.account.AccountStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Request body to update an account: its lifecycle status plus the complete new realm and role
 * sets. There is no create or delete verb — a soft delete is simply this request with {@code status
 * = BLOCKED}. Bean-validated at the controller (RFC 9457 {@code 400} with {@code fieldErrors} on a
 * missing status). Realms come in as strings and role references as ids; the service rejects an
 * unknown realm or role id with a {@code 400} (a separate check from these field constraints — the
 * same unknown-reference shape as roles' unknown permission).
 *
 * @param status the new lifecycle status (required)
 * @param realms the new, complete set of realm names (may be empty/omitted to clear)
 * @param roleIds the new, complete set of role ids (may be empty/omitted to clear)
 */
public record AccountUpdateRequest(
    @NotNull AccountStatus status, Set<String> realms, Set<Long> roleIds) {}
