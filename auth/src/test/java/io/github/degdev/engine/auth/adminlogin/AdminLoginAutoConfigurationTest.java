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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.degdev.engine.auth.account.AccountRepository;
import io.github.degdev.engine.auth.account.Realm;
import io.github.degdev.engine.auth.adminlogin.web.AdminLoginController;
import io.github.degdev.engine.auth.password.PasswordAutoConfiguration;
import io.github.degdev.engine.auth.password.PasswordHasher;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.auth.token.TokenAutoConfiguration;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the opt-in mechanism: with the three auth auto-configurations on the classpath, the admin
 * login beans are mounted ONLY when {@code nobilis.auth.admin-login.enabled=true}, and are absent
 * when the flag is missing or false. Also pins the always-on vs. configured-on split for the token
 * primitives, and that an enabled login actually issues a verifiable token while rejecting a bad
 * password.
 */
class AdminLoginAutoConfigurationTest {

  private static final String EMAIL = "admin@example.org";
  private static final String RAW_PASSWORD = "s3cret-admin-pw";
  // Generated at runtime — no key or hash value is ever written to a committed file.
  private static final String JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String PASSWORD_HASH = new PasswordHasher().hash(RAW_PASSWORD);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TokenAutoConfiguration.class,
                  PasswordAutoConfiguration.class,
                  AdminLoginAutoConfiguration.class));

  @Test
  void passwordHasherIsAlwaysAvailable() {
    runner.run(context -> assertThat(context).hasSingleBean(PasswordHasher.class));
  }

  @Test
  void jwtServiceAppearsOnlyWhenASecretIsConfigured() {
    runner.run(context -> assertThat(context).doesNotHaveBean(JwtService.class));
    runner
        .withPropertyValues("nobilis.auth.jwt.secret=" + JWT_KEY)
        .run(context -> assertThat(context).hasSingleBean(JwtService.class));
  }

  @Test
  void adminLoginIsNotMountedWhenFlagIsMissing() {
    runner
        .withPropertyValues("nobilis.auth.jwt.secret=" + JWT_KEY)
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(AdminLoginController.class)
                    .doesNotHaveBean(AdminLoginService.class));
  }

  @Test
  void adminLoginIsNotMountedWhenFlagIsFalse() {
    runner
        .withPropertyValues(
            "nobilis.auth.jwt.secret=" + JWT_KEY, "nobilis.auth.admin-login.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(AdminLoginController.class)
                    .doesNotHaveBean(AdminLoginService.class));
  }

  @Test
  void adminLoginIsMountedWhenEnabled() {
    enabledRunner()
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(AdminLoginController.class)
                    .hasSingleBean(AdminLoginService.class));
  }

  @Test
  void enabledLoginIssuesAVerifiableTokenAndRejectsABadPassword() {
    enabledRunner()
        .run(
            context -> {
              AdminLoginService service = context.getBean(AdminLoginService.class);
              JwtService jwtService = context.getBean(JwtService.class);

              String token = service.login(EMAIL, RAW_PASSWORD);

              assertThat(jwtService.validate(token).subject()).isEqualTo(EMAIL);
              assertThat(jwtService.validate(token).roles()).containsExactly("ADMIN");
              assertThatThrownBy(() -> service.login(EMAIL, "wrong-password"))
                  .isInstanceOf(InvalidCredentialsException.class);
            });
  }

  @Test
  void configAdminIssuesThickTokenWithoutAnAccountRow() {
    // No JPA or account persistence is loaded here — the config-admin is a deliberate stateless
    // bootstrap path that mints a thick token from properties with no account row. The absent
    // AccountRepository bean locks that: coupling admin login to a DB account would break this
    // test.
    enabledRunner()
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AccountRepository.class);

              AdminLoginService service = context.getBean(AdminLoginService.class);
              JwtService jwtService = context.getBean(JwtService.class);

              var claims = jwtService.validate(service.login(EMAIL, RAW_PASSWORD));

              assertThat(claims.realms()).containsExactly(Realm.ADMIN.name());
              assertThat(claims.permissions())
                  .containsExactlyInAnyOrderElementsOf(EnginePermissions.ALL);
            });
  }

  private ApplicationContextRunner enabledRunner() {
    return runner.withPropertyValues(
        "nobilis.auth.admin-login.enabled=true",
        "nobilis.auth.admin-login.email=" + EMAIL,
        "nobilis.auth.admin-login.password-hash=" + PASSWORD_HASH,
        "nobilis.auth.jwt.secret=" + JWT_KEY);
  }
}
