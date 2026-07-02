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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the always-available token primitive {@link JwtService}. It is gated only on a signing
 * secret being configured ({@code nobilis.auth.jwt.secret}) — not on any feature flag — so the
 * engine starts cleanly when auth is on the classpath but unconfigured, and gains a working JWT
 * service the moment a key is supplied. Registered for opt-in discovery via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}; the host
 * application is never blanket component-scanned.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class TokenAutoConfiguration {

  /**
   * Provides the JWT service when a signing secret is present.
   *
   * @param properties the configured signing key and TTL
   * @return a {@link JwtService} bound to the system UTC clock
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "nobilis.auth.jwt", name = "secret")
  public JwtService jwtService(JwtProperties properties) {
    return new JwtService(properties, Clock.systemUTC());
  }
}
