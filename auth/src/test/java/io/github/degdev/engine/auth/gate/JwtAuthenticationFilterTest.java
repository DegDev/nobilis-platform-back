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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.degdev.engine.auth.token.JwtProperties;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit test for the inbound gate mechanism. Drives {@link JwtAuthenticationFilter} directly with a
 * mock request/response and a capturing filter chain, asserting that a valid token populates {@link
 * AuthContextHolder} for the duration of the request (with working {@code hasPermission}), that a
 * missing or invalid token proceeds unauthenticated (the gate never rejects), and that the holder
 * is always cleared once the chain completes (no cross-request bleed).
 */
class JwtAuthenticationFilterTest {

  private static final String PERMISSION = "SETTINGS_MANAGE";
  private static final String OTHER_PERMISSION = "ACCOUNT_MANAGE";

  private static JwtService jwtService() {
    return new JwtService(
        new JwtProperties(CryptoKeyGenerator.generateBase64Key(), Duration.ofMinutes(30)),
        Clock.systemUTC());
  }

  @Test
  void validTokenPopulatesHolderDuringRequestThenClears() throws Exception {
    JwtService jwtService = jwtService();
    String token =
        jwtService.issue(
            "admin@example.org", List.of("ADMIN"), List.of("ADMIN"), List.of(PERMISSION));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);

    boolean[] present = {false};
    boolean[] hadPermission = {false};
    boolean[] hadOtherPermission = {true};
    FilterChain chain =
        (req, res) -> {
          present[0] = AuthContextHolder.current().isPresent();
          hadPermission[0] = AuthContextHolder.hasPermission(PERMISSION);
          hadOtherPermission[0] = AuthContextHolder.hasPermission(OTHER_PERMISSION);
        };

    new JwtAuthenticationFilter(jwtService).doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(present[0]).isTrue();
    assertThat(hadPermission[0]).isTrue();
    assertThat(hadOtherPermission[0]).isFalse();
    assertThat(AuthContextHolder.current()).isEmpty();
  }

  @Test
  void noTokenLeavesHolderEmptyAndStillProceeds() throws Exception {
    boolean[] chainCalled = {false};
    boolean[] present = {true};
    FilterChain chain =
        (req, res) -> {
          chainCalled[0] = true;
          present[0] = AuthContextHolder.current().isPresent();
        };

    new JwtAuthenticationFilter(jwtService())
        .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(chainCalled[0]).isTrue();
    assertThat(present[0]).isFalse();
    assertThat(AuthContextHolder.current()).isEmpty();
  }

  @Test
  void invalidTokenProceedsUnauthenticated() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer not-a-valid-token");

    boolean[] chainCalled = {false};
    boolean[] present = {true};
    FilterChain chain =
        (req, res) -> {
          chainCalled[0] = true;
          present[0] = AuthContextHolder.current().isPresent();
        };

    new JwtAuthenticationFilter(jwtService())
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chainCalled[0]).isTrue();
    assertThat(present[0]).isFalse();
  }
}
