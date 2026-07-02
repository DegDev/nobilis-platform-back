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
package io.github.degdev.engine.admin.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import io.github.degdev.engine.auth.token.JwtProperties;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives the real gate + contour filter pair over a probe controller via standalone {@link MockMvc}
 * (no Spring context, no database). The gate is added before the contour, mirroring the production
 * ordering wired in {@link ContourSecurityConfiguration}: the gate binds a valid token's claims,
 * then the contour admits or rejects.
 *
 * <ul>
 *   <li>Anonymous request to a protected path &rarr; 401.
 *   <li>Valid ADMIN-realm token &rarr; the request reaches the handler (200).
 *   <li>Valid token without the ADMIN realm &rarr; 403.
 *   <li>The open login path bypasses the contour (reaches the dispatcher; 404 here, never 401).
 * </ul>
 */
class AdminContourFilterTest {

  @RestController
  static class ProbeController {
    @GetMapping("/protected/ping")
    String ping() {
      return "pong";
    }
  }

  private JwtService jwtService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.jwtService =
        new JwtService(
            new JwtProperties(CryptoKeyGenerator.generateBase64Key(), Duration.ofMinutes(30)),
            Clock.systemUTC());
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .addFilters(new JwtAuthenticationFilter(jwtService), new AdminContourFilter())
            .build();
  }

  @Test
  void anonymousProtectedPathIsUnauthorized() throws Exception {
    mockMvc.perform(get("/protected/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminTokenReachesProtectedPath() throws Exception {
    String token =
        jwtService.issue("admin@example.org", List.of("ADMIN"), List.of("ADMIN"), List.of());
    mockMvc
        .perform(get("/protected/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("pong"));
  }

  @Test
  void tokenWithoutAdminRealmIsForbidden() throws Exception {
    String token = jwtService.issue("client@example.org", List.of(), List.of("CLIENT"), List.of());
    mockMvc
        .perform(get("/protected/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void openLoginPathBypassesContour() throws Exception {
    // No handler is mapped at the login path in this probe; reaching the dispatcher (404) — not a
    // 401 from the contour — proves the path is open.
    mockMvc.perform(get("/auth/admin/login")).andExpect(status().isNotFound());
  }
}
