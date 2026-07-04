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

import io.github.degdev.engine.auth.role.Role;
import io.github.degdev.engine.auth.role.RoleRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of EXISTING {@link Account}s: list, read, and update (status + realms + roles).
 * Creation and DB-login are a later milestone — a created account cannot authenticate yet — so this
 * service deliberately has neither; a "delete" is a soft one, expressed as a status change to
 * {@link AccountStatus#BLOCKED} through {@link #update} (the schema has no {@code DELETED}, so
 * there is no hard delete and no {@code account_role}/{@code account_realm} cascade to reason
 * about).
 *
 * <p>It returns the {@link AccountDto} read-model rather than entities, and builds it INSIDE the
 * transaction: {@code account_realm} and {@code account_role} are both lazy
 * {@code @ElementCollection }/{@code @ManyToMany}, and identities are a separate {@link
 * AccountIdentity} query — with open-in-view off, all of that must be materialized here or a caller
 * serializing later would hit a {@code LazyInitializationException}. Unknown realm/role references
 * are rejected with domain exceptions the admin layer maps to {@code 400} (the same
 * unknown-reference shape as roles' unknown permission).
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * AccountServiceAutoConfiguration}, gated on JPA being active, so a stateless host has no account
 * service (and therefore no accounts controller). Lombok {@code @RequiredArgsConstructor} wires the
 * {@code final} repositories; {@code @Slf4j} provides {@code log}.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;
  private final AccountIdentityRepository identityRepository;
  private final RoleRepository roleRepository;

  /**
   * Lists accounts, one page at a time.
   *
   * @param pageable the page request (page number, size, sort)
   * @return the requested page of accounts as {@link AccountDto}s
   */
  @Transactional(readOnly = true)
  public Page<AccountDto> list(Pageable pageable) {
    return accountRepository.findAll(pageable).map(this::toDto);
  }

  /**
   * Reads one account by id.
   *
   * @param id the account id
   * @return the account read-model, or empty if none has that id
   */
  @Transactional(readOnly = true)
  public Optional<AccountDto> get(Long id) {
    return accountRepository.findById(id).map(this::toDto);
  }

  /**
   * Updates an account's lifecycle status, realms and roles in one operation — the complete new
   * sets replace the old. A soft delete is this call with {@code status = BLOCKED}.
   *
   * @param id the account id
   * @param status the new lifecycle status
   * @param realmNames the new, complete set of realm names ({@code null}/empty clears them)
   * @param roleIds the new, complete set of role ids ({@code null}/empty clears them)
   * @return the updated account read-model, or empty if no account has that id
   * @throws UnknownRealmException if any realm name is not an engine {@link Realm}
   * @throws UnknownRoleException if any role id resolves to no role
   */
  @Transactional
  public Optional<AccountDto> update(
      Long id, AccountStatus status, Set<String> realmNames, Set<Long> roleIds) {
    return accountRepository
        .findById(id)
        .map(
            account -> {
              Set<Realm> realms = resolveRealms(realmNames);
              Set<Role> roles = resolveRoles(roleIds);
              // Self-lockout guard deferred: the only caller today is the config-admin, whose
              // subject is an email with no Account row (and no subject->Account resolver exists),
              // so it cannot lock itself out. A future DB-login pass must add the guard here.
              account.setStatus(status);
              account.replaceRealms(realms);
              account.replaceRoles(roles);
              log.debug(
                  "Updated account {} -> status {}, {} realm(s), {} role(s)",
                  id,
                  status,
                  realms.size(),
                  roles.size());
              return toDto(accountRepository.save(account));
            });
  }

  /**
   * Maps an account to its read-model, materializing the two lazy collections and the identity rows
   * inside the current transaction (open-in-view is off).
   */
  private AccountDto toDto(Account account) {
    List<AccountDto.RoleRef> roles =
        account.getRoles().stream()
            .map(role -> new AccountDto.RoleRef(role.getId(), role.getCode(), role.getName()))
            .toList();
    Set<Realm> realms = account.getRealms();
    List<AccountDto.IdentityRef> identities =
        identityRepository.findByAccount(account).stream()
            .map(
                identity ->
                    new AccountDto.IdentityRef(
                        identity.getProviderType(), identity.getExternalId()))
            .toList();
    return new AccountDto(account.getId(), account.getStatus(), realms, roles, identities);
  }

  /** Resolves realm names to {@link Realm}s, rejecting any the engine does not define. */
  private static Set<Realm> resolveRealms(Set<String> names) {
    if (names == null || names.isEmpty()) {
      return Set.of();
    }
    Set<String> known = Arrays.stream(Realm.values()).map(Enum::name).collect(Collectors.toSet());
    Set<String> unknown = new LinkedHashSet<>(names);
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new UnknownRealmException(unknown);
    }
    return names.stream().map(Realm::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Resolves role ids to {@link Role}s, rejecting any id that matches no role. */
  private Set<Role> resolveRoles(Set<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    List<Role> found = roleRepository.findAllById(ids);
    if (found.size() != ids.size()) {
      Set<Long> unknown = new LinkedHashSet<>(ids);
      found.forEach(role -> unknown.remove(role.getId()));
      throw new UnknownRoleException(unknown);
    }
    return new LinkedHashSet<>(found);
  }
}
