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
package io.github.degdev.engine.auth.adminlogin;

import io.github.degdev.engine.auth.adminlogin.web.AdminLoginController;
import io.github.degdev.engine.auth.password.PasswordAutoConfiguration;
import io.github.degdev.engine.auth.password.PasswordHasher;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.auth.token.TokenAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * The opt-in mechanism, demonstrated. The admin login service and controller are mounted ONLY when
 * {@code nobilis.auth.admin-login.enabled=true}; with the flag absent or false this whole
 * configuration is skipped and neither bean exists. This is the engine's opt-in principle in action
 * — a capability is off until the host explicitly enables it, not enabled by blanket component-scan
 * (CLAUDE.md, "Boundary: engine vs domain").
 *
 * <p>Enabling the feature requires a JWT signing secret too (see {@link TokenAutoConfiguration});
 * if {@code nobilis.auth.jwt.secret} is missing, {@link JwtService} is absent and context startup
 * fails loudly rather than serving unsignable logins. Discovered via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {TokenAutoConfiguration.class, PasswordAutoConfiguration.class})
@ConditionalOnProperty(prefix = "nobilis.auth.admin-login", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AdminLoginProperties.class)
public class AdminLoginAutoConfiguration {

  /**
   * Provides the admin login service.
   *
   * @param properties the configured admin credential
   * @param passwordHasher the BCrypt verifier
   * @param jwtService the token issuer
   * @return the wired {@link AdminLoginService}
   */
  @Bean
  public AdminLoginService adminLoginService(
      AdminLoginProperties properties, PasswordHasher passwordHasher, JwtService jwtService) {
    return new AdminLoginService(properties, passwordHasher, jwtService);
  }

  /**
   * Provides the login HTTP endpoint.
   *
   * @param adminLoginService the service it delegates to
   * @return the {@link AdminLoginController}
   */
  @Bean
  public AdminLoginController adminLoginController(AdminLoginService adminLoginService) {
    return new AdminLoginController(adminLoginService);
  }
}
