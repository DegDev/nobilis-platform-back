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
package io.github.degdev.engine.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.auth.adminlogin.web.AdminLoginController;
import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import io.github.degdev.engine.auth.password.PasswordHasher;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the milestone-03 admin host boots as a STATELESS web application — with {@code common}'s
 * JPA/Flyway on the classpath but no database configured — because {@link AdminApplication}
 * excludes the DataSource/JPA/Flyway auto-configurations. Also asserts the opt-in auth wiring
 * mounts: the login endpoint, the gate mechanism, and the host contour policy.
 *
 * <p>All secrets are generated at runtime via {@link DynamicPropertySource}; no key or hash value
 * is ever written to a committed file.
 */
@SpringBootTest
class AdminApplicationTest {

  @Autowired private ApplicationContext context;

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("nobilis.auth.jwt.secret", CryptoKeyGenerator::generateBase64Key);
    registry.add("nobilis.auth.admin-login.enabled", () -> "true");
    registry.add("nobilis.auth.admin-login.email", () -> "admin@example.org");
    registry.add(
        "nobilis.auth.admin-login.password-hash",
        () -> new PasswordHasher().hash("s3cret-admin-pw"));
  }

  @Test
  void bootsWithoutADatabase() {
    assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
  }

  @Test
  void mountsLoginGateAndContour() {
    assertThat(context.getBeanNamesForType(AdminLoginController.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(JwtAuthenticationFilter.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(AdminContourFilter.class)).hasSize(1);
  }
}
