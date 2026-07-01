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

import io.github.degdev.engine.auth.token.JwtException;
import io.github.degdev.engine.auth.token.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The engine's first inbound gate. On each request it reads a bearer token from the {@code
 * Authorization} header, verifies it with {@link JwtService}, and binds the resulting claims to the
 * thread via {@link AuthContextHolder} for the duration of the request — always clearing them in a
 * {@code finally} so nothing bleeds across pooled request threads.
 *
 * <p>This filter is a pure MECHANISM, not a policy: a missing or invalid token simply proceeds with
 * no claims (unauthenticated). It never rejects a request. Deciding what an anonymous or
 * under-privileged caller may reach — the admin/client contour, per-endpoint rules — is the host's
 * job (milestone 03), expressed by reading {@link AuthContextHolder}, not baked in here.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  /**
   * Creates the gate filter.
   *
   * @param jwtService the verifier for inbound tokens
   */
  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = bearerToken(request);
      if (token != null) {
        try {
          AuthContextHolder.set(jwtService.validate(token));
        } catch (JwtException invalid) {
          // Invalid or expired token: proceed unauthenticated. The gate never rejects — endpoints
          // and the host's contour rules decide what a caller with no claims may reach.
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      AuthContextHolder.clear();
    }
  }

  private static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
