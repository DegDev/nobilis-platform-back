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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves an account's <em>effective</em> permissions: the union of the permissions granted by
 * every role the account holds. This is the walk {@code account → account_role → role →
 * role_permission} flattened to a set of permission strings, and it is the input a permission gate
 * checks against.
 *
 * <p>Lives with {@link Account} (the subject of the walk) so the dependency runs one way, {@code
 * account → role}, with no package cycle. It reads the account's role and permission collections,
 * so it must be called with the account still attached to an open persistence context (both
 * collections are lazy). A utility class — the private constructor is an intentional exception to
 * the no-boilerplate house style.
 */
public final class PermissionResolver {

  private PermissionResolver() {}

  /**
   * Computes the effective permission set for an account by unioning its roles' permissions.
   *
   * @param account the account to resolve; must be attached to an open persistence context
   * @return an unmodifiable set of the permission strings the account effectively holds
   */
  public static Set<String> resolve(Account account) {
    return account.getRoles().stream()
        .flatMap(role -> role.getPermissions().stream())
        .collect(Collectors.toUnmodifiableSet());
  }
}
