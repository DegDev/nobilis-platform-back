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
package io.github.degdev.engine.auth.gate;

import io.github.degdev.engine.auth.token.AuthClaims;
import java.util.Optional;

/**
 * Thread-bound holder for the authenticated request's {@link AuthClaims}. {@link
 * JwtAuthenticationFilter} sets it at the start of a request from a valid token and clears it when
 * the request completes, so application code (and the {@link #hasPermission(String)} helper) can
 * read "who is calling and what may they do" without threading the claims through every call.
 *
 * <p>The lifecycle methods are package-private on purpose: only the filter manages the binding.
 * Reading ({@link #current()}, {@link #hasPermission(String)}) is public. A utility class — the
 * private constructor is an intentional exception to the no-boilerplate house style.
 */
public final class AuthContextHolder {

  private static final ThreadLocal<AuthClaims> CURRENT = new ThreadLocal<>();

  private AuthContextHolder() {}

  /**
   * Binds the claims to the current thread.
   *
   * @param claims the verified claims for the in-flight request
   */
  static void set(AuthClaims claims) {
    CURRENT.set(claims);
  }

  /** Unbinds any claims from the current thread. Must be called when the request completes. */
  static void clear() {
    CURRENT.remove();
  }

  /**
   * {@return the claims bound to the current thread, or empty if the request is unauthenticated}
   */
  public static Optional<AuthClaims> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * Tests whether the current request's token grants a permission.
   *
   * @param permission the permission string to check (e.g. an {@code EnginePermissions} constant)
   * @return {@code true} if a token is bound and its permissions contain {@code permission}
   */
  public static boolean hasPermission(String permission) {
    AuthClaims claims = CURRENT.get();
    return claims != null && claims.permissions().contains(permission);
  }
}
