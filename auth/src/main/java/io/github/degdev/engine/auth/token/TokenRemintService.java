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
package io.github.degdev.engine.auth.token;

import java.time.Clock;
import java.time.Duration;

/**
 * Silently re-mints a still-usable JWT into a fresh one, from the presented token's own claims — no
 * database round-trip (recon-confirmed feasible: {@link JwtService#issue(String, java.util.List,
 * java.util.List, java.util.List, java.time.Instant)} takes only claim values). Two grace windows
 * bound the leniency this endpoint alone is allowed:
 *
 * <ul>
 *   <li>{@link #GRACE} — a token up to 5 minutes past {@code exp} is still remintable (covers the
 *       reactive case: a request raced an expiring token and got a 401). Beyond that, hard reject.
 *   <li>{@link #STALENESS_CAP} — {@code loginAt} may not be more than 8 hours in the past,
 *       regardless of how many re-mints have chained since the original credential-verified login.
 *       This is the only reason {@code loginAt} exists: it is carried forward UNCHANGED on every
 *       re-mint, so chaining re-mints can never push the effective session past this cap.
 * </ul>
 */
public class TokenRemintService {

  static final Duration GRACE = Duration.ofMinutes(5);
  static final Duration STALENESS_CAP = Duration.ofHours(8);

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final Clock clock;

  public TokenRemintService(JwtService jwtService, Clock clock) {
    this.jwtService = jwtService;
    this.clock = clock;
  }

  /**
   * Re-mints a fresh token from the bearer token carried in the request's raw {@code Authorization}
   * header. Deliberately does not consult {@link
   * io.github.degdev.engine.auth.gate.AuthContextHolder} — by the time an expired token reaches
   * this endpoint the gate has already treated the request as anonymous, so the claims must be read
   * from the raw header instead.
   *
   * @param authorizationHeader the raw {@code Authorization} header value, or {@code null}
   * @return a freshly signed token carrying the same claims and the original {@code loginAt}
   * @throws TokenRemintException if the header is missing/malformed, the token fails verification,
   *     it expired beyond {@link #GRACE}, or the session exceeded {@link #STALENESS_CAP}
   */
  public String remint(String authorizationHeader) {
    String token = bearerToken(authorizationHeader);
    AuthClaims claims;
    try {
      claims = jwtService.validateForRemint(token, GRACE);
    } catch (JwtException invalid) {
      throw new TokenRemintException();
    }
    if (Duration.between(claims.loginAt(), clock.instant()).compareTo(STALENESS_CAP) > 0) {
      throw new TokenRemintException();
    }
    return jwtService.issue(
        claims.subject(), claims.roles(), claims.realms(), claims.permissions(), claims.loginAt());
  }

  private static String bearerToken(String authorizationHeader) {
    if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
      return authorizationHeader.substring(BEARER_PREFIX.length());
    }
    throw new TokenRemintException();
  }
}
