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
package io.github.degdev.engine.auth.account;

import java.util.List;
import java.util.Set;

/**
 * The admin read-model of an {@link Account}: its id, lifecycle {@link AccountStatus}, the {@link
 * Realm}s and {@link io.github.degdev.engine.auth.role.Role}s it holds, and how it authenticates.
 * Built by {@link AccountService} INSIDE the read transaction (the two lazy collections and the
 * identity rows are materialized there, since open-in-view is off) and returned as-is by the admin
 * accounts controller — it is the account feature's own contract, so it lives in {@code auth}, not
 * {@code admin} (a service cannot depend on the web module).
 *
 * <p>Roles are exposed lean — {@code id} + {@code code}/{@code name}, no permission set — because
 * an account view does not need each role's full permissions and skipping them avoids initializing
 * yet another lazy collection. Identities carry ONLY the provider and its external id: the {@code
 * secretHash} on {@link AccountIdentity} is never a field here, so it structurally cannot leave the
 * server (the same secret-masking discipline as settings).
 *
 * @param id the account id
 * @param status the lifecycle status
 * @param realms the realms the account may enter
 * @param roles the roles assigned to the account (lean references)
 * @param identities how the account proves who it is (provider + external id only)
 */
public record AccountDto(
    Long id,
    AccountStatus status,
    Set<Realm> realms,
    List<RoleRef> roles,
    List<IdentityRef> identities) {

  /**
   * A role assigned to the account, by its stable id and human-readable code/name — deliberately
   * without the role's permission set (an account view does not need it, and omitting it keeps
   * {@code Role.permissions} un-initialized).
   *
   * @param id the role id (what an edit form submits back)
   * @param code the unique role code
   * @param name the human-readable label
   */
  public record RoleRef(Long id, String code, String name) {}

  /**
   * One identity binding of the account: the provider and its external id ONLY. The stored secret
   * hash is intentionally absent — it must never leave the server.
   *
   * @param provider the authenticating provider
   * @param externalId the provider-scoped identifier
   */
  public record IdentityRef(ProviderType provider, String externalId) {}
}
