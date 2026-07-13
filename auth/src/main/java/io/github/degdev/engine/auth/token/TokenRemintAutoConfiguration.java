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

import io.github.degdev.engine.auth.token.web.TokenRemintController;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the silent token re-mint endpoint, opt-in. Like {@link GateAutoConfiguration} it is gated
 * only on a signing secret being configured ({@code nobilis.auth.jwt.secret}) — re-mint is a token
 * primitive, not specific to any one login flow, so it is available wherever {@link JwtService} is.
 * Registered via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration(after = TokenAutoConfiguration.class)
@ConditionalOnProperty(prefix = "nobilis.auth.jwt", name = "secret")
public class TokenRemintAutoConfiguration {

  /**
   * Provides the re-mint service when a signing secret (hence a {@link JwtService}) is present.
   *
   * @param jwtService the issuer/verifier the service re-mints through
   * @return the {@link TokenRemintService}, bound to the system UTC clock
   */
  @Bean
  @ConditionalOnMissingBean
  public TokenRemintService tokenRemintService(JwtService jwtService) {
    return new TokenRemintService(jwtService, Clock.systemUTC());
  }

  /**
   * Provides the re-mint HTTP endpoint.
   *
   * @param tokenRemintService the service it delegates to
   * @return the {@link TokenRemintController}
   */
  @Bean
  public TokenRemintController tokenRemintController(TokenRemintService tokenRemintService) {
    return new TokenRemintController(tokenRemintService);
  }
}
