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
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String KEY = CryptoKeyGenerator.generateBase64Key();
  private static final Instant FIXED = Instant.parse("2026-06-30T12:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(30);

  private static JwtService serviceAt(Instant now) {
    return new JwtService(new JwtProperties(KEY, TTL), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void roundTripRecoversSubjectAndRoles() {
    JwtService service = serviceAt(FIXED);

    AuthClaims claims =
        service.validate(service.issue("admin@example.org", List.of("ADMIN", "OPS")));

    assertThat(claims.subject()).isEqualTo("admin@example.org");
    assertThat(claims.roles()).containsExactly("ADMIN", "OPS");
    assertThat(claims.issuedAt()).isEqualTo(FIXED);
    assertThat(claims.expiresAt()).isEqualTo(FIXED.plus(TTL));
  }

  @Test
  void tokenHasThreeSegments() {
    assertThat(serviceAt(FIXED).issue("s", List.of()).split("\\.")).hasSize(3);
  }

  @Test
  void expiredTokenIsRejected() {
    String token = serviceAt(FIXED).issue("admin@example.org", List.of("ADMIN"));

    JwtService later = serviceAt(FIXED.plus(TTL).plusSeconds(1));

    assertThatThrownBy(() -> later.validate(token))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void tamperedSignatureIsRejected() {
    String token = serviceAt(FIXED).issue("admin@example.org", List.of("ADMIN"));
    char[] chars = token.toCharArray();
    int last = chars.length - 1;
    chars[last] = chars[last] == 'A' ? 'B' : 'A';

    assertThatThrownBy(() -> serviceAt(FIXED).validate(new String(chars)))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    String foreign =
        new JwtService(
                new JwtProperties(CryptoKeyGenerator.generateBase64Key(), TTL),
                Clock.fixed(FIXED, ZoneOffset.UTC))
            .issue("admin@example.org", List.of("ADMIN"));

    assertThatThrownBy(() -> serviceAt(FIXED).validate(foreign))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void malformedTokenIsRejected() {
    assertThatThrownBy(() -> serviceAt(FIXED).validate("not-a-jwt"))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("3 segments");
  }

  @Test
  void constructionRejectsMissingSecret() {
    assertThatThrownBy(() -> new JwtService(new JwtProperties("  ", TTL), Clock.systemUTC()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nobilis.auth.jwt.secret");
  }

  @Test
  void constructionRejectsTooShortKey() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> new JwtService(new JwtProperties(shortKey, TTL), Clock.systemUTC()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256 bits");
  }
}
