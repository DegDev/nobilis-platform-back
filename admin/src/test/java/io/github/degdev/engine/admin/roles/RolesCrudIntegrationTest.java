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
package io.github.degdev.engine.admin.roles;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.auth.account.Account;
import io.github.degdev.engine.auth.account.AccountRepository;
import io.github.degdev.engine.auth.account.AccountStatus;
import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.auth.role.Role;
import io.github.degdev.engine.auth.role.RoleRepository;
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
 * Full-stack slice of the roles API against a real PostgreSQL 18 (Testcontainers), the second
 * consumer of the admin REST framework. Boots with the stateless exclusions RESET to empty so
 * persistence is active — which also proves the roles controller mounts only via {@code
 * RoleAdminAutoConfiguration} (gated on the {@code RoleService} auth contributes) and that auth's
 * repositories are discovered in this host purely via {@code AuthPersistenceAutoConfiguration}'s
 * package; if either failed the context would not start.
 *
 * <p>Asserts the framework contract for a second controller (unchanged gate/contour/interceptor):
 * {@code 401} anonymous, {@code 403} wrong realm, {@code 403} missing {@code ACCOUNT_MANAGE}; a
 * CRUD round-trip; {@code 409} on a duplicate code and on deleting an in-use role (with the
 * reference count); {@code 400} on an unknown permission; the permission catalog; and {@code
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
class RolesCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String BASE = "/admin/api/roles";

  @Autowired private WebApplicationContext context;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AccountRepository accountRepository;

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
  void nonAdminRealmIsRejectedByTheContour() throws Exception {
    String token =
        jwtService.issue(
            "user", List.of(), List.of("CLIENT"), List.of(EnginePermissions.ACCOUNT_MANAGE));

    mockMvc
        .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminWithoutAccountManageIsRejectedByTheInterceptor() throws Exception {
    String token = jwtService.issue("admin", List.of(), List.of("ADMIN"), List.of());

    mockMvc
        .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void createReadUpdateDelete() throws Exception {
    String auth = bearer(adminToken());

    String created =
        mockMvc
            .perform(
                post(BASE)
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"editor\",\"name\":\"Editor\",\"permissions\":[\"ACCOUNT_MANAGE\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("editor"))
            .andExpect(jsonPath("$.permissions", containsInAnyOrder("ACCOUNT_MANAGE")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = MAPPER.readTree(created).get("id").asLong();

    mockMvc
        .perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Editor"));

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Senior editor\",\"permissions\":[\"SETTINGS_MANAGE\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Senior editor"))
        .andExpect(jsonPath("$.permissions", containsInAnyOrder("SETTINGS_MANAGE")));

    mockMvc
        .perform(delete(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void duplicateCodeIsRejectedWith409() throws Exception {
    String auth = bearer(adminToken());
    String payload = "{\"code\":\"dup\",\"name\":\"Dup\",\"permissions\":[]}";

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
  void unknownPermissionIsRejectedWith400() throws Exception {
    mockMvc
        .perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"bad\",\"name\":\"Bad\",\"permissions\":[\"BOGUS_PERM\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("BOGUS_PERM")));
  }

  @Test
  void deletingAnInUseRoleIsRejectedWith409AndTheReferenceCount() throws Exception {
    Role role = roleRepository.save(new Role("in-use-role", "In use"));
    Account account = new Account(AccountStatus.ACTIVE);
    account.addRole(role);
    accountRepository.save(account);

    mockMvc
        .perform(
            delete(BASE + "/" + role.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail", containsString("assigned to 1")));
  }

  @Test
  void permissionCatalogReturnsTheEnginePermissions() throws Exception {
    mockMvc
        .perform(get(BASE + "/permissions").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", containsInAnyOrder("ACCOUNT_MANAGE", "SETTINGS_MANAGE")));
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
                  "{\"code\":\"page-role-" + i + "\",\"name\":\"n" + i + "\",\"permissions\":[]}"));
    }

    mockMvc
        .perform(get(BASE + "?size=2&sort=code").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(greaterThanOrEqualTo(3)));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.ACCOUNT_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
