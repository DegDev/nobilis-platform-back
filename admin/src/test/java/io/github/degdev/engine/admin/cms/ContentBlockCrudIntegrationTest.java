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
package io.github.degdev.engine.admin.cms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full-stack slice of the content-blocks API against a real PostgreSQL 18 (Testcontainers),
 * following the {@code RolesCrudIntegrationTest} shape. Boots with the stateless exclusions RESET
 * to empty so persistence is active — which also proves the controller mounts only via {@code
 * ContentBlockWebAutoConfiguration} (gated on the {@code ContentBlockService} common contributes).
 *
 * <p>Asserts the framework contract: {@code 401} anonymous, {@code 403} missing {@code
 * CONTENT_MANAGE}; a create/status/translation/delete round-trip; {@code 409} on a duplicate key;
 * {@code 404} on an unknown key; {@code 400} on an unsupported locale; and {@code PagedModel}
 * pagination.
 *
 * <p>All keys are generated at runtime via {@link DynamicPropertySource}; none is ever committed.
 */
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false"
    })
@Testcontainers
class ContentBlockCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String BASE = "/api/admin/content-blocks";

  @Autowired private WebApplicationContext context;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;

  private MockMvc mockMvc;

  /** Supplies the JWT key at runtime so no key value ever sits in a properties file. */
  @DynamicPropertySource
  static void secrets(DynamicPropertyRegistry registry) {
    registry.add("nobilis.auth.jwt.secret", () -> TEST_JWT_KEY);
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(gateFilter, contourFilter).build();
  }

  @Test
  void anonymousListIsRejectedByTheContour() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
  }

  @Test
  void adminWithoutContentManageIsRejectedByTheInterceptor() throws Exception {
    String token = jwtService.issue("admin", List.of(), List.of("ADMIN"), List.of());

    mockMvc
        .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void createPublishTranslateDelete() throws Exception {
    String auth = bearer(adminToken());

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"home.hero\",\"status\":\"DRAFT\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.key").value("home.hero"))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    mockMvc
        .perform(
            put(BASE + "/home.hero/status")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    mockMvc
        .perform(
            put(BASE + "/home.hero/translations/ru")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Добро пожаловать\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations.ru").value("Добро пожаловать"));

    mockMvc
        .perform(get(BASE + "/home.hero").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations.ru").value("Добро пожаловать"));

    mockMvc
        .perform(
            delete(BASE + "/home.hero/translations/ru").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE + "/home.hero").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations").isEmpty());

    mockMvc
        .perform(delete(BASE + "/home.hero").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE + "/home.hero").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void duplicateKeyIsRejectedWith409() throws Exception {
    String auth = bearer(adminToken());
    String payload = "{\"key\":\"dup.block\",\"status\":\"DRAFT\"}";

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void unsupportedLocaleIsRejectedWith400() throws Exception {
    String auth = bearer(adminToken());
    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"locale.block\",\"status\":\"DRAFT\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put(BASE + "/locale.block/translations/fr")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Bonjour\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("fr")));
  }

  @Test
  void listIsPaged() throws Exception {
    String auth = bearer(adminToken());
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(
          post(BASE)
              .header(HttpHeaders.AUTHORIZATION, auth)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"key\":\"page.block." + i + "\",\"status\":\"DRAFT\"}"));
    }

    mockMvc
        .perform(get(BASE + "?size=2&sort=key").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(
            jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.CONTENT_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
