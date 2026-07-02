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
package io.github.degdev.engine.admin.security;

import io.github.degdev.engine.auth.account.Realm;
import io.github.degdev.engine.auth.gate.AuthContextHolder;
import io.github.degdev.engine.auth.token.AuthClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The admin contour: the policy the engine's gate mechanism deliberately left to the host (B0.3).
 * The gate ({@code JwtAuthenticationFilter}) only reads a token into {@link AuthContextHolder} and
 * never rejects; this filter turns those claims into an admission decision for the admin side.
 *
 * <ul>
 *   <li>Open paths (the login endpoint and the error dispatch) always pass through.
 *   <li>No claims bound (anonymous / missing / invalid token) on any other path &rarr; 401.
 *   <li>Claims present but without the {@link Realm#ADMIN} realm &rarr; 403.
 * </ul>
 *
 * <p>It reads the claims the gate bound to the request, so it MUST run immediately after the gate —
 * ordering is made deterministic in {@link ContourSecurityConfiguration}. The gate stays a
 * mechanism; the contour is policy that lives in the host.
 */
public class AdminContourFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/auth/admin/login";
  private static final String ERROR_PATH = "/error";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isOpen(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    Optional<AuthClaims> claims = AuthContextHolder.current();
    if (claims.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (!claims.get().realms().contains(Realm.ADMIN.name())) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isOpen(HttpServletRequest request) {
    String path = request.getRequestURI();
    return LOGIN_PATH.equals(path) || ERROR_PATH.equals(path);
  }
}
