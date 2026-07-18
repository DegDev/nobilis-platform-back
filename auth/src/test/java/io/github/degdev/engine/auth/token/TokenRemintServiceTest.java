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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenRemintServiceTest {

  private static final String KEY = CryptoKeyGenerator.generateBase64Key();
  private static final Duration TTL = Duration.ofMinutes(30);
  private static final Instant LOGIN_AT = Instant.parse("2026-06-30T09:00:00Z");
  private static final Instant ISSUED_AT = LOGIN_AT.plus(Duration.ofMinutes(20));
  private static final Instant EXPIRES_AT = ISSUED_AT.plus(TTL);

  private static JwtService jwtServiceAt(Instant now) {
    return new JwtService(new JwtProperties(KEY, TTL), Clock.fixed(now, ZoneOffset.UTC));
  }

  private static TokenRemintService serviceAt(Instant now) {
    return new TokenRemintService(jwtServiceAt(now), Clock.fixed(now, ZoneOffset.UTC));
  }

  private static String tokenExpiringAt(Instant exp) {
    // Mint through a service whose clock is fixed at (exp - TTL), so the token's own exp lands
    // exactly on the requested instant.
    return new JwtService(new JwtProperties(KEY, TTL), Clock.fixed(exp.minus(TTL), ZoneOffset.UTC))
        .issue("admin@example.org", List.of("ADMIN"), List.of("ADMIN"), List.of("X"), LOGIN_AT);
  }

  @Test
  void remintsAStillValidTokenAndCarriesLoginAtForward() {
    String token = tokenExpiringAt(EXPIRES_AT);

    String reminted = serviceAt(ISSUED_AT).remint("Bearer " + token);

    AuthClaims claims = jwtServiceAt(ISSUED_AT).validate(reminted);
    assertThat(claims.subject()).isEqualTo("admin@example.org");
    assertThat(claims.realms()).containsExactly("ADMIN");
    assertThat(claims.permissions()).containsExactly("X");
    assertThat(claims.loginAt()).isEqualTo(LOGIN_AT);
  }

  @Test
  void acceptsATokenFourMinutesFiftyNineSecondsPastExpiry() {
    String token = tokenExpiringAt(EXPIRES_AT);
    Instant now = EXPIRES_AT.plus(Duration.ofMinutes(4).plusSeconds(59));

    String reminted = serviceAt(now).remint("Bearer " + token);

    assertThat(reminted).isNotBlank();
  }

  @Test
  void rejectsATokenFiveMinutesOneSecondPastExpiry() {
    String token = tokenExpiringAt(EXPIRES_AT);
    Instant now = EXPIRES_AT.plus(Duration.ofMinutes(5).plusSeconds(1));
    TokenRemintService service = serviceAt(now);

    assertThatThrownBy(() -> service.remint("Bearer " + token))
        .isInstanceOf(TokenRemintException.class);
  }

  @Test
  void acceptsASessionSevenHoursFiftyNineMinutesOld() {
    Instant now = LOGIN_AT.plus(Duration.ofHours(7).plusMinutes(59));
    String token = tokenExpiringAt(now.plus(Duration.ofMinutes(1)));

    String reminted = serviceAt(now).remint("Bearer " + token);

    assertThat(reminted).isNotBlank();
  }

  @Test
  void rejectsASessionEightHoursOneMinuteOld() {
    Instant now = LOGIN_AT.plus(Duration.ofHours(8).plusMinutes(1));
    String token = tokenExpiringAt(now.plus(Duration.ofMinutes(1)));
    TokenRemintService service = serviceAt(now);

    assertThatThrownBy(() -> service.remint("Bearer " + token))
        .isInstanceOf(TokenRemintException.class);
  }

  @Test
  void rejectsAMissingAuthorizationHeader() {
    TokenRemintService service = serviceAt(ISSUED_AT);

    assertThatThrownBy(() -> service.remint(null)).isInstanceOf(TokenRemintException.class);
  }

  @Test
  void rejectsAHeaderWithoutTheBearerPrefix() {
    String token = tokenExpiringAt(EXPIRES_AT);
    TokenRemintService service = serviceAt(ISSUED_AT);

    assertThatThrownBy(() -> service.remint(token)).isInstanceOf(TokenRemintException.class);
  }

  @Test
  void rejectsATamperedToken() {
    String token = tokenExpiringAt(EXPIRES_AT);
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
    TokenRemintService service = serviceAt(ISSUED_AT);

    assertThatThrownBy(() -> service.remint("Bearer " + tampered))
        .isInstanceOf(TokenRemintException.class);
  }

  @Test
  void chainedRemintsCannotExtendTheSessionPastTheStalenessCap() {
    // First remint, still within TTL.
    String token = tokenExpiringAt(EXPIRES_AT);
    Instant firstRemintAt = EXPIRES_AT.minus(Duration.ofMinutes(1));
    String remintedOnce = serviceAt(firstRemintAt).remint("Bearer " + token);

    // The reminted token's own exp is (firstRemintAt + TTL); remint it again just past that, but
    // still comfortably within the 8h staleness cap measured from the ORIGINAL loginAt.
    Instant secondRemintAt = firstRemintAt.plus(TTL).plusSeconds(30);
    assertThat(Duration.between(LOGIN_AT, secondRemintAt))
        .isLessThan(TokenRemintService.STALENESS_CAP);
    String remintedTwice = serviceAt(secondRemintAt).remint("Bearer " + remintedOnce);

    AuthClaims claims = jwtServiceAt(secondRemintAt).validate(remintedTwice);
    assertThat(claims.loginAt()).isEqualTo(LOGIN_AT);

    // Push a third remint attempt past the cap: it must be rejected even though the token itself
    // (freshly reminted a moment ago) is nowhere near its own exp.
    Instant thirdRemintAt = LOGIN_AT.plus(TokenRemintService.STALENESS_CAP).plusSeconds(1);
    TokenRemintService thirdService = serviceAt(thirdRemintAt);
    assertThatThrownBy(() -> thirdService.remint("Bearer " + remintedTwice))
        .isInstanceOf(TokenRemintException.class);
  }
}
