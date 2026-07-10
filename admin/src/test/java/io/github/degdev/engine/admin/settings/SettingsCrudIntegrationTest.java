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
package io.github.degdev.engine.admin.settings;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.token.JwtService;
import io.github.degdev.engine.common.crypto.CryptoKeyGenerator;
import java.util.List;
import org.hamcrest.Matchers;
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
 * Full-stack slice of the settings API against a real PostgreSQL 18 (Testcontainers), with the
 * stateless-default exclusions RESET to empty ({@code spring.autoconfigure.exclude=}) so
 * persistence is un-excluded — the profile-gated DB-return in action, proven end to end. It also
 * proves that {@code common}'s {@code SettingsAutoConfiguration} registers {@code Setting}/{@code
 * SettingRepository} into this host purely via its auto-configuration package (the host does not
 * component-scan {@code common}): if that failed the context would not start.
 *
 * <p>MockMvc is built over the real context ({@code webAppContextSetup}) with the gate and contour
 * filters added explicitly, in order, so the servlet-layer policy runs ahead of the
 * DispatcherServlet exactly as in production; the MVC permission interceptor and the ProblemDetail
 * advice come from the context itself. Asserts the framework contract: {@code 401} anonymous,
 * {@code 403} wrong realm (contour) and {@code 403} missing permission (interceptor), a CRUD
 * round-trip, secret masking on reads, {@code 400} validation with {@code fieldErrors}, and {@code
 * PagedModel} pagination.
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
class SettingsCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_MASTER_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String BASE = "/admin/api/settings";

  @Autowired private WebApplicationContext context;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;

  private MockMvc mockMvc;

  /** Supplies the crypto and JWT keys at runtime so no key value ever sits in a properties file. */
  @DynamicPropertySource
  static void secrets(DynamicPropertyRegistry registry) {
    registry.add("nobilis.crypto.master-key", () -> TEST_MASTER_KEY);
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
  void nonAdminRealmIsRejectedByTheContour() throws Exception {
    String token =
        jwtService.issue(
            "user", List.of(), List.of("CLIENT"), List.of(EnginePermissions.SETTINGS_MANAGE));

    mockMvc
        .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminWithoutPermissionIsRejectedByTheInterceptor() throws Exception {
    String token = jwtService.issue("admin", List.of(), List.of("ADMIN"), List.of());

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"x\",\"value\":\"y\",\"secret\":false}"))
        // Interceptor rejection leaves as RFC 9457 problem+json (unlike the servlet-layer contour).
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void createReadUpdateDelete() throws Exception {
    String auth = bearer(adminToken());

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"portal.title\",\"value\":\"Nobilis\",\"secret\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.value").value("Nobilis"));

    mockMvc
        .perform(get(BASE + "/portal.title").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").value("Nobilis"));

    mockMvc
        .perform(
            put(BASE + "/portal.title")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Nobilis Platform\",\"secret\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").value("Nobilis Platform"));

    mockMvc
        .perform(delete(BASE + "/portal.title").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE + "/portal.title").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void secretValueIsMaskedOnReads() throws Exception {
    String auth = bearer(adminToken());

    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"bank.password\",\"value\":\"hunter2\",\"secret\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.secret").value(true))
        .andExpect(jsonPath("$.value").value(Matchers.nullValue()));

    mockMvc
        .perform(get(BASE + "/bank.password").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").value(true))
        .andExpect(jsonPath("$.value").value(Matchers.nullValue()))
        // The plaintext never leaves the server on a read path — not even the ciphertext.
        .andExpect(content().string(Matchers.not(Matchers.containsString("hunter2"))));
  }

  @Test
  void validationFailureReportsFieldErrors() throws Exception {
    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"\",\"value\":\"y\",\"secret\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors").isArray())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("key"));
  }

  @Test
  void listIsPaged() throws Exception {
    String auth = bearer(adminToken());
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(
          post(BASE)
              .header(HttpHeaders.AUTHORIZATION, auth)
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  "{\"key\":\"page.key." + i + "\",\"value\":\"v" + i + "\",\"secret\":false}"));
    }

    mockMvc
        .perform(get(BASE + "?size=2&sort=key").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(greaterThanOrEqualTo(3)));
  }

  @Test
  void listByKeyPrefixReturnsOnlyMatchingKeysAndNeverLeaksASecretValue() throws Exception {
    String auth = bearer(adminToken());

    mockMvc.perform(
        post(BASE)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"key\":\"integration.figma.api_key\",\"value\":\"secret-token\","
                    + "\"secret\":true}"));
    mockMvc.perform(
        post(BASE)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"portal.title\",\"value\":\"Nobilis\",\"secret\":false}"));

    mockMvc
        .perform(get(BASE + "?keyPrefix=integration.").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].key").value("integration.figma.api_key"))
        .andExpect(jsonPath("$.content[0].secret").value(true))
        .andExpect(jsonPath("$.content[0].value").value(Matchers.nullValue()))
        // The secret's plaintext (nor its ciphertext) never appears in the prefix-filtered
        // response.
        .andExpect(content().string(Matchers.not(Matchers.containsString("secret-token"))));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.SETTINGS_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
