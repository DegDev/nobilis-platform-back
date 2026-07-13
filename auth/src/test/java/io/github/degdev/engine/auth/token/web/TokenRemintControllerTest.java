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
package io.github.degdev.engine.auth.token.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.auth.token.JwtProperties;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.auth.token.TokenRemintService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end HTTP slice for the token re-mint endpoint via standalone {@link MockMvc} (real
 * controller and service, no Spring context). A valid bearer token yields 200 with a fresh token; a
 * missing/malformed header yields 401 (from {@code TokenRemintException}'s
 * {@code @ResponseStatus}).
 */
class TokenRemintControllerTest {

  private JwtService jwtService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.jwtService =
        new JwtService(
            new JwtProperties(CryptoKeyGenerator.generateBase64Key(), Duration.ofMinutes(30)),
            Clock.systemUTC());
    TokenRemintController controller =
        new TokenRemintController(new TokenRemintService(jwtService, Clock.systemUTC()));
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void validBearerTokenReturnsAFreshToken() throws Exception {
    String token =
        jwtService.issue("admin@example.org", List.of("ADMIN"), List.of("ADMIN"), List.of());

    mockMvc
        .perform(post("/auth/admin/remint").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void missingAuthorizationHeaderReturns401() throws Exception {
    mockMvc.perform(post("/auth/admin/remint")).andExpect(status().isUnauthorized());
  }

  @Test
  void malformedAuthorizationHeaderReturns401() throws Exception {
    mockMvc
        .perform(post("/auth/admin/remint").header("Authorization", "not-a-bearer-token"))
        .andExpect(status().isUnauthorized());
  }
}
