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
package io.github.degdev.engine.admin.accounts;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.admin.security.AdminContourFilter;
import io.github.degdev.engine.auth.account.Account;
import io.github.degdev.engine.auth.account.AccountIdentity;
import io.github.degdev.engine.auth.account.AccountIdentityRepository;
import io.github.degdev.engine.auth.account.AccountRepository;
import io.github.degdev.engine.auth.account.AccountStatus;
import io.github.degdev.engine.auth.account.ProviderType;
import io.github.degdev.engine.auth.account.Realm;
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
 * Full-stack slice of the accounts API against a real PostgreSQL 18 (Testcontainers), the third
 * consumer of the admin REST framework. Boots with the stateless exclusions RESET to empty so
 * persistence is active — which also proves the accounts controller mounts only via {@code
 * AccountAdminAutoConfiguration} (gated on the {@code AccountService} auth contributes).
 *
 * <p>Asserts the framework contract (unchanged gate/contour/interceptor): {@code 401} anonymous,
 * {@code 403} wrong realm, {@code 403} missing {@code ACCOUNT_MANAGE}; the manage round-trip
 * (status/realms/roles update, soft-delete via {@code BLOCKED}); {@code 400} on an unknown realm
 * and an unknown role id; {@code 404} on an unknown account; {@code PagedModel} pagination. Two
 * account specifics: the {@code secretHash} on an identity NEVER appears in any response body, and
 * the two lazy collections ({@code account_realm} + {@code account_role}) serialize cleanly under
 * {@code open-in-view=false} (a request would 500 if the service left them un-initialized).
 *
 * <p>Accounts are seeded directly through the repositories — there is no create endpoint by design.
 * All keys are generated at runtime via {@link DynamicPropertySource}; none is ever committed.
 */
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false"
    })
@Testcontainers
class AccountsCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String BASE = "/admin/api/accounts";
  // A clearly-fake sentinel — the point is to prove it never appears in a response body.
  private static final String SECRET_HASH_SENTINEL = "secret-hash-should-never-leak";

  @Autowired private WebApplicationContext context;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountIdentityRepository identityRepository;
  @Autowired private RoleRepository roleRepository;

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
  void getReturnsTheAccountWithRealmsRolesAndIdentitiesButNeverTheSecretHash() throws Exception {
    Role role = roleRepository.save(new Role("viewer", "Viewer"));
    long id = seedAccount(AccountStatus.ACTIVE, Realm.ADMIN, role);
    seedIdentity(id, "reader@example.org", SECRET_HASH_SENTINEL);

    mockMvc
        .perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.realms", containsInAnyOrder("ADMIN")))
        .andExpect(jsonPath("$.roles[0].code").value("viewer"))
        .andExpect(jsonPath("$.identities[0].provider").value("EMAIL"))
        .andExpect(jsonPath("$.identities[0].externalId").value("reader@example.org"))
        // The secret hash — neither its value nor the field name — ever leaves the server.
        .andExpect(content().string(not(containsString(SECRET_HASH_SENTINEL))))
        .andExpect(content().string(not(containsString("secretHash"))))
        .andExpect(content().string(not(containsString("secret_hash"))));
  }

  @Test
  void unknownAccountIs404() throws Exception {
    mockMvc
        .perform(get(BASE + "/99999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateChangesStatusRealmsAndRoles() throws Exception {
    Role viewer = roleRepository.save(new Role("viewer2", "Viewer 2"));
    Role editor = roleRepository.save(new Role("editor2", "Editor 2"));
    long id = seedAccount(AccountStatus.ACTIVE, Realm.CLIENT, viewer);

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"status\":\"BLOCKED\",\"realms\":[\"ADMIN\",\"CLIENT\"],\"roleIds\":["
                        + editor.getId()
                        + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.realms", containsInAnyOrder("ADMIN", "CLIENT")))
        .andExpect(jsonPath("$.roles[0].code").value("editor2"));
  }

  @Test
  void softDeleteBlocksTheAccountAndClearsRealmsAndRoles() throws Exception {
    Role role = roleRepository.save(new Role("temp-role", "Temp"));
    long id = seedAccount(AccountStatus.ACTIVE, Realm.ADMIN, role);

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BLOCKED\",\"realms\":[],\"roleIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.realms", emptyIterable()))
        .andExpect(jsonPath("$.roles", emptyIterable()));
  }

  @Test
  void unknownRealmIsRejectedWith400() throws Exception {
    long id = seedAccount(AccountStatus.ACTIVE, Realm.ADMIN);

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"realms\":[\"BOGUS_REALM\"],\"roleIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("BOGUS_REALM")));
  }

  @Test
  void unknownRoleIdIsRejectedWith400() throws Exception {
    long id = seedAccount(AccountStatus.ACTIVE, Realm.ADMIN);

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"realms\":[\"ADMIN\"],\"roleIds\":[88888888]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("88888888")));
  }

  @Test
  void missingStatusIsRejectedWith400AndFieldError() throws Exception {
    long id = seedAccount(AccountStatus.ACTIVE, Realm.ADMIN);

    mockMvc
        .perform(
            put(BASE + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"realms\":[\"ADMIN\"],\"roleIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
  }

  @Test
  void listIsPagedAndSerializesLazyCollections() throws Exception {
    Role role = roleRepository.save(new Role("list-role", "List role"));
    for (int i = 0; i < 3; i++) {
      seedAccount(AccountStatus.ACTIVE, Realm.ADMIN, role);
    }

    mockMvc
        .perform(get(BASE + "?size=2").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(greaterThanOrEqualTo(3)))
        // The lazy realms/roles are materialized in the service tx — present, not a 500.
        .andExpect(jsonPath("$.content[0].realms").exists())
        .andExpect(jsonPath("$.content[0].roles").exists());
  }

  private long seedAccount(AccountStatus status, Realm realm, Role... roles) {
    Account account = new Account(status);
    account.addRealm(realm);
    for (Role role : roles) {
      account.addRole(role);
    }
    return accountRepository.save(account).getId();
  }

  private void seedIdentity(long accountId, String externalId, String secretHash) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    identityRepository.save(
        new AccountIdentity(account, ProviderType.EMAIL, externalId, secretHash));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.ACCOUNT_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
