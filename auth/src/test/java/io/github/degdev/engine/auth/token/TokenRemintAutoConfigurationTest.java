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

import io.github.degdev.engine.auth.token.web.TokenRemintController;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the opt-in mechanism for the re-mint endpoint: mounted whenever a signing secret is
 * configured — the same trigger as {@link TokenAutoConfiguration} itself — and absent otherwise,
 * with no dependency on the admin-login flag (re-mint is a token primitive, not login-specific).
 */
class TokenRemintAutoConfigurationTest {

  private static final String JWT_KEY = CryptoKeyGenerator.generateBase64Key();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TokenAutoConfiguration.class, TokenRemintAutoConfiguration.class));

  @Test
  void remintEndpointIsAbsentWithoutASigningSecret() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(TokenRemintController.class)
                .doesNotHaveBean(TokenRemintService.class));
  }

  @Test
  void remintEndpointIsMountedWhenASigningSecretIsConfigured() {
    runner
        .withPropertyValues("nobilis.auth.jwt.secret=" + JWT_KEY)
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(TokenRemintController.class)
                    .hasSingleBean(TokenRemintService.class));
  }
}
