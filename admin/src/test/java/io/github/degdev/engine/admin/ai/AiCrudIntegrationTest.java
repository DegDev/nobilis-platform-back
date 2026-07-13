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
package io.github.degdev.engine.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.ai.profile.AiSecretStore;
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
import org.springframework.context.ApplicationContext;
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
 * Full-stack slice of the AI-profile admin API against a real PostgreSQL 18 (Testcontainers), the
 * sixth consumer of the admin REST framework (5th {@code @RestController} scan-exclude instance,
 * after Settings/Roles/Accounts/CMS/Notifications). Boots with the stateless exclusions RESET to
 * empty so persistence is active — proves {@link AiProfileController} mounts only via {@code
 * AiAdminAutoConfiguration} (gated on the {@code AiProfileService} the {@code ai} module
 * contributes) and exactly once (not double-registered by the host's component scan).
 *
 * <p>{@code nobilis.ai.base-url} points at an unreachable host ({@code 127.0.0.1:1}) — proves the
 * health-check endpoint returns {@code 200} with {@code ok=false} rather than a {@code 500} on a
 * real connection failure, per {@code OllamaHealthCheckService}'s never-throws contract.
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
class AiCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String TEST_MASTER_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String BASE = "/api/admin/ai";

  @Autowired private WebApplicationContext context;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;
  @Autowired private AiSecretStore secretStore;

  private MockMvc mockMvc;

  /** Supplies keys/config at runtime so no value ever sits in a properties file. */
  @DynamicPropertySource
  static void secrets(DynamicPropertyRegistry registry) {
    registry.add("nobilis.auth.jwt.secret", () -> TEST_JWT_KEY);
    registry.add("nobilis.crypto.master-key", () -> TEST_MASTER_KEY);
    registry.add("nobilis.ai.base-url", () -> "http://127.0.0.1:1");
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(gateFilter, contourFilter).build();
  }

  @Test
  void controllerMountsExactlyOnce() {
    assertThat(applicationContext.getBeanNamesForType(AiProfileController.class)).hasSize(1);
  }

  @Test
  void anonymousIsRejectedByTheContour() throws Exception {
    mockMvc.perform(get(BASE + "/purposes")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminWithoutAiManageIsRejectedByTheInterceptor() throws Exception {
    String token = jwtService.issue("admin", List.of(), List.of("ADMIN"), List.of());

    mockMvc
        .perform(get(BASE + "/purposes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  void purposesAndProvidersListTheSeededCatalog() throws Exception {
    String auth = bearer(adminToken());

    mockMvc
        .perform(get(BASE + "/purposes").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", contains("default")));

    mockMvc
        .perform(get(BASE + "/providers?purpose=default").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("ollama"));
  }

  @Test
  void descriptorReturnsTheOllamaDefaultFieldSetIncludingNoThink() throws Exception {
    mockMvc
        .perform(
            get(BASE + "/descriptor?purpose=default&provider=ollama")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[5].fieldKey").value("no-think"))
        .andExpect(jsonPath("$[5].type").value("BOOLEAN"))
        .andExpect(jsonPath("$[5].category").value("OPERATIONAL"))
        .andExpect(jsonPath("$[5].defaultValue").value("true"));
  }

  @Test
  void saveValidProfileThenGetProfileReflectsIt() throws Exception {
    String auth = bearer(adminToken());

    mockMvc
        .perform(
            post(BASE + "/profile")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"purpose\":\"default\",\"provider\":\"ollama\","
                        + "\"params\":{\"model\":\"llama3.1\",\"no-think\":\"true\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providerCode").value("ollama"))
        .andExpect(jsonPath("$.params.model").value("llama3.1"))
        .andExpect(jsonPath("$.params['no-think']").value("true"));

    mockMvc
        .perform(get(BASE + "/profile?purpose=default").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.params.model").value("llama3.1"));
  }

  @Test
  void saveWithAnUnknownFieldKeyReturnsProblemDetailWithTheMessageKey() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"purpose\":\"default\",\"provider\":\"ollama\","
                        + "\"params\":{\"bogus-field\":\"x\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("bogus-field")));
  }

  @Test
  void saveWithAnOutOfBoundsNumberReturnsProblemDetail() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"purpose\":\"default\",\"provider\":\"ollama\","
                        + "\"params\":{\"temperature\":\"5\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("temperature")));
  }

  @Test
  void saveWithBlankPurposeIsRejectedByBeanValidation() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"\",\"provider\":\"ollama\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("purpose"));
  }

  @Test
  void getProfileNeverLeaksAStoredSecretsPlaintext() throws Exception {
    secretStore.store("default.ollama.some-secret", "sk-super-secret-value");

    String body =
        mockMvc
            .perform(
                get(BASE + "/profile?purpose=default")
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("sk-super-secret-value");
  }

  @Test
  void healthCheckReturnsOkFalseForAnUnreachableOllamaWithoutA500() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/health-check")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"default\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(false));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.AI_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
