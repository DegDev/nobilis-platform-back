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
package io.github.degdev.engine.auth.adminlogin;

import io.github.degdev.engine.auth.account.Realm;
import io.github.degdev.engine.auth.password.PasswordHasher;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.token.JwtService;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Verifies admin email/password credentials against the configured single admin account and, on
 * success, issues a signed JWT carrying the {@code ADMIN} role. The check is deliberately
 * all-or-nothing: a wrong email, a wrong password, or an unconfigured credential all yield the same
 * {@link InvalidCredentialsException}, so the endpoint leaks nothing about which part failed.
 *
 * <p>The token is "thick": the config-admin has no {@code account} row (that is a later pass), so
 * its realms and permissions are hardcoded here — the {@link Realm#ADMIN} realm and the full {@link
 * EnginePermissions#ALL} catalog. A real, database-backed account resolves these from its roles
 * instead.
 */
@RequiredArgsConstructor
public class AdminLoginService {

  private static final String ADMIN_ROLE = "ADMIN";

  private final AdminLoginProperties properties;
  private final PasswordHasher passwordHasher;
  private final JwtService jwtService;

  /**
   * Authenticates the admin and mints a token.
   *
   * @param email the submitted email
   * @param rawPassword the submitted plaintext password
   * @return a signed JWT for the admin identity
   * @throws InvalidCredentialsException if the credentials do not match the configured admin
   */
  public String login(String email, String rawPassword) {
    boolean valid =
        email != null
            && rawPassword != null
            && properties.email() != null
            && properties.passwordHash() != null
            && properties.email().equalsIgnoreCase(email.trim())
            && passwordHasher.matches(rawPassword, properties.passwordHash());
    if (!valid) {
      throw new InvalidCredentialsException();
    }
    return jwtService.issue(
        properties.email(),
        List.of(ADMIN_ROLE),
        List.of(Realm.ADMIN.name()),
        List.copyOf(EnginePermissions.ALL));
  }
}
