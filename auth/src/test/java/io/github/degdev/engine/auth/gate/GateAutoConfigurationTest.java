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

import io.github.degdev.engine.auth.token.TokenAutoConfiguration;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the gate's opt-in mechanism: the {@link JwtAuthenticationFilter} is mounted ONLY when a
 * JWT signing secret is configured ({@code nobilis.auth.jwt.secret}) — the same trigger as {@link
 * TokenAutoConfiguration}'s {@code JwtService} — and is absent otherwise, so a host is never
 * force-secured just by having auth on the classpath.
 */
class GateAutoConfigurationTest {

  // Generated at runtime — no key value is ever written to a committed file.
  private static final String JWT_KEY = CryptoKeyGenerator.generateBase64Key();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(TokenAutoConfiguration.class, GateAutoConfiguration.class));

  @Test
  void gateFilterIsAbsentWithoutASecret() {
    runner.run(context -> assertThat(context).doesNotHaveBean(JwtAuthenticationFilter.class));
  }

  @Test
  void gateFilterIsMountedWhenASecretIsConfigured() {
    runner
        .withPropertyValues("nobilis.auth.jwt.secret=" + JWT_KEY)
        .run(context -> assertThat(context).hasSingleBean(JwtAuthenticationFilter.class));
  }
}
