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

import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.auth.token.TokenAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the inbound {@link JwtAuthenticationFilter}, opt-in. Like {@link TokenAutoConfiguration}
 * it is gated only on a signing secret being configured ({@code nobilis.auth.jwt.secret}) — with no
 * secret there is no {@link JwtService} and no gate, so a host on the classpath is never
 * force-secured. Registered via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}; the host opts
 * in by depending on auth and supplying a key, not by being component-scanned.
 *
 * <p>A plain {@code jakarta.servlet.Filter} bean is auto-registered with the embedded servlet
 * container by Spring Boot (mapped to all requests), so no explicit registration bean is needed.
 */
@AutoConfiguration(after = TokenAutoConfiguration.class)
@ConditionalOnProperty(prefix = "nobilis.auth.jwt", name = "secret")
public class GateAutoConfiguration {

  /**
   * Provides the gate filter when a signing secret (hence a {@link JwtService}) is present.
   *
   * @param jwtService the verifier for inbound tokens
   * @return the {@link JwtAuthenticationFilter}
   */
  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
    return new JwtAuthenticationFilter(jwtService);
  }
}
