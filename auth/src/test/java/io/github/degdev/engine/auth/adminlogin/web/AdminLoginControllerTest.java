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
package io.github.degdev.engine.auth.adminlogin.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.auth.adminlogin.AdminLoginProperties;
import io.github.degdev.engine.auth.adminlogin.AdminLoginService;
import io.github.degdev.engine.auth.password.PasswordHasher;
import io.github.degdev.engine.auth.token.JwtProperties;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end HTTP slice for the admin login endpoint via standalone {@link MockMvc} (real
 * controller, service, BCrypt verification, and JWT issuance; no Spring context or database).
 * Correct credentials yield 200 with a token; a wrong password yields 401 (from {@code
 * InvalidCredentialsException}'s {@code @ResponseStatus}).
 */
class AdminLoginControllerTest {

  private static final String EMAIL = "admin@example.org";
  private static final String RAW_PASSWORD = "s3cret-admin-pw";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    PasswordHasher passwordHasher = new PasswordHasher();
    JwtService jwtService =
        new JwtService(
            new JwtProperties(CryptoKeyGenerator.generateBase64Key(), Duration.ofMinutes(30)),
            Clock.systemUTC());
    AdminLoginProperties properties =
        new AdminLoginProperties(true, EMAIL, passwordHasher.hash(RAW_PASSWORD));
    AdminLoginController controller =
        new AdminLoginController(new AdminLoginService(properties, passwordHasher, jwtService));

    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void validCredentialsReturnAToken() throws Exception {
    mockMvc
        .perform(
            post("/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(EMAIL, RAW_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void wrongPasswordReturns401() throws Exception {
    mockMvc
        .perform(
            post("/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(EMAIL, "wrong-password")))
        .andExpect(status().isUnauthorized());
  }

  private static String body(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }
}
