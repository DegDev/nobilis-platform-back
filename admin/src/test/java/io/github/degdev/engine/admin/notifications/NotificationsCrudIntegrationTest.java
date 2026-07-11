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
package io.github.degdev.engine.admin.notifications;

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
 * Full-stack slice of the notifications admin API against a real PostgreSQL 18 (Testcontainers),
 * following the {@code ContentBlockCrudIntegrationTest} shape exactly. Boots with the stateless
 * exclusions RESET to empty so persistence is active — which also proves the controllers mount only
 * via {@code NotificationsWebAutoConfiguration} (gated on the {@code NotificationsService} common
 * contributes).
 *
 * <p>Asserts: {@code 401} anonymous, a create/template/translation/delete round-trip; {@code 409}
 * on a duplicate type key; {@code 400} on an unsupported locale; ru/ro translations round-trip.
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
class NotificationsCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String TEST_JWT_KEY = CryptoKeyGenerator.generateBase64Key();
  private static final String TYPES = "/admin/api/notification-types";
  private static final String TEMPLATES = "/admin/api/notification-templates";

  @Autowired private WebApplicationContext context;
  @Autowired private JwtAuthenticationFilter gateFilter;
  @Autowired private AdminContourFilter contourFilter;
  @Autowired private JwtService jwtService;

  private MockMvc mockMvc;

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
    mockMvc.perform(get(TYPES)).andExpect(status().isUnauthorized());
  }

  @Test
  void createUpdateListAndGetDeleteType() throws Exception {
    String auth = bearer(adminToken());

    // Create
    mockMvc
        .perform(
            post(TYPES)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"key\":\"order.created\",\"enabled\":true,\"description\":\"New order\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.key").value("order.created"))
        .andExpect(jsonPath("$.enabled").value(true));

    // Update
    mockMvc
        .perform(
            put(TYPES + "/order.created")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"description\":\"Disabled\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.description").value("Disabled"));

    // Get
    mockMvc
        .perform(get(TYPES + "/order.created").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value("order.created"));

    // Delete
    mockMvc
        .perform(delete(TYPES + "/order.created").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    // Gone
    mockMvc
        .perform(get(TYPES + "/order.created").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void duplicateTypeKeyIsRejectedWith409() throws Exception {
    String auth = bearer(adminToken());
    String payload = "{\"key\":\"dup.type\",\"enabled\":true,\"description\":null}";

    mockMvc
        .perform(
            post(TYPES)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(TYPES)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void createTemplateUpsertTranslationsRemoveTranslationDeleteTemplate() throws Exception {
    String auth = bearer(adminToken());

    // Create a type
    mockMvc.perform(
        post(TYPES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"test.notify\",\"enabled\":true,\"description\":null}"));

    // Create a template (EMAIL)
    mockMvc
        .perform(
            post(TEMPLATES)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"test.notify\",\"transport\":\"EMAIL\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transport").value("EMAIL"));

    // Upsert RU translation
    mockMvc
        .perform(
            put(TEMPLATES + "/test.notify/EMAIL/translations/ru")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subject\":\"\u0417\u0430\u043a\u0430\u0437\",\"body\":\"\u0423"
                        + " \u0432\u0430\u0441 \u043d\u043e\u0432\u044b\u0439"
                        + " \u0437\u0430\u043a\u0430\u0437\"}"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.translations.ru.body")
                .value(
                    "\u0423 \u0432\u0430\u0441 \u043d\u043e\u0432\u044b\u0439"
                        + " \u0437\u0430\u043a\u0430\u0437"));

    // Upsert RO translation
    mockMvc
        .perform(
            put(TEMPLATES + "/test.notify/EMAIL/translations/ro")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subject\":\"Comand\u0103\",\"body\":\"Ave\u021bi o comand\u0103"
                        + " nou\u0103\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations.ro.body").value("Ave\u021bi o comand\u0103 nou\u0103"));

    // Get the template — both translations present
    mockMvc
        .perform(get(TEMPLATES + "/test.notify/EMAIL").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations.ru").exists())
        .andExpect(jsonPath("$.translations.ro").exists());

    // Remove RO translation
    mockMvc
        .perform(
            delete(TEMPLATES + "/test.notify/EMAIL/translations/ro")
                .header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());

    // RO is gone, RU remains
    mockMvc
        .perform(get(TEMPLATES + "/test.notify/EMAIL").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.translations.ru").exists())
        .andExpect(jsonPath("$.translations.ro").doesNotExist());

    // Delete the template
    mockMvc
        .perform(delete(TEMPLATES + "/test.notify/EMAIL").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isNoContent());
  }

  @Test
  void listTemplatesWithoutTypeFilterExposesTypeKey() throws Exception {
    String auth = bearer(adminToken());

    mockMvc.perform(
        post(TYPES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"list.notify.test\",\"enabled\":true,\"description\":null}"));

    mockMvc.perform(
        post(TEMPLATES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"typeKey\":\"list.notify.test\",\"transport\":\"EMAIL\"}"));

    mockMvc.perform(
        put(TEMPLATES + "/list.notify.test/EMAIL/translations/ru")
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"subject\":\"Subj\",\"body\":\"Body\"}"));

    // Unlike get-by-key, the unfiltered list doesn't pre-warm the lazy `type` association via a
    // prior requireType() lookup — this is the path a real request took when it hit
    // LazyInitializationException with open-in-view=false. Page.map runs after the transaction
    // (and its session) has closed, so both the lazy `type` and the `translations` collection
    // must already be initialized by the time the DTOs are mapped.
    mockMvc
        .perform(get(TEMPLATES + "?size=100").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "\"typeKey\":\"list.notify.test\",\"transport\":\"EMAIL\"")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "\"translations\":{\"ru\":{\"subject\":\"Subj\",\"body\":\"Body\"}}")));
  }

  @Test
  void listTemplatesFilteredByTypeExcludesOtherTypes() throws Exception {
    String auth = bearer(adminToken());

    mockMvc.perform(
        post(TYPES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"filter.type.a\",\"enabled\":true,\"description\":null}"));
    mockMvc.perform(
        post(TYPES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"filter.type.b\",\"enabled\":true,\"description\":null}"));

    mockMvc.perform(
        post(TEMPLATES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"typeKey\":\"filter.type.a\",\"transport\":\"EMAIL\"}"));
    mockMvc.perform(
        post(TEMPLATES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"typeKey\":\"filter.type.b\",\"transport\":\"EMAIL\"}"));

    mockMvc
        .perform(
            get(TEMPLATES + "?typeKey=filter.type.a&size=100")
                .header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[*].typeKey",
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("filter.type.a"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"typeKey\":\"filter.type.b\""))));
  }

  @Test
  void unsupportedLocaleIsRejectedWith400() throws Exception {
    String auth = bearer(adminToken());
    mockMvc.perform(
        post(TYPES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"key\":\"locale.test\",\"enabled\":true,\"description\":null}"));
    mockMvc.perform(
        post(TEMPLATES)
            .header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"typeKey\":\"locale.test\",\"transport\":\"TELEGRAM\"}"));

    mockMvc
        .perform(
            put(TEMPLATES + "/locale.test/TELEGRAM/translations/fr")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":null,\"body\":\"Bonjour\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("fr")));
  }

  @Test
  void listTypesIsPaged() throws Exception {
    String auth = bearer(adminToken());
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(
          post(TYPES)
              .header(HttpHeaders.AUTHORIZATION, auth)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"key\":\"page.type." + i + "\",\"enabled\":true,\"description\":null}"));
    }

    mockMvc
        .perform(get(TYPES + "?size=2&sort=key").header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.size").value(2))
        .andExpect(jsonPath("$.content[0].key").exists())
        .andExpect(jsonPath("$.content[0].enabled").value(true));
  }

  private String adminToken() {
    return jwtService.issue(
        "admin", List.of(), List.of("ADMIN"), List.of(EnginePermissions.NOTIFICATIONS_MANAGE));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
