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
package io.github.degdev.engine.app.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.common.cms.ContentBlockService;
import io.github.degdev.engine.common.cms.ContentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full-stack slice of the portal's public CMS read path against a real PostgreSQL 18
 * (Testcontainers), mirroring {@code ContentBlockCrudIntegrationTest}'s shape. Boots with the
 * stateless exclusions RESET to empty so persistence is active — which also proves {@link
 * PortalContentController} mounts only via {@link PortalContentWebAutoConfiguration} (gated on the
 * {@link ContentBlockService} common contributes).
 *
 * <p>Unlike the admin CMS slice, there is no contour or JWT to set up: the portal host has no
 * security dependency at all, so every request here is anonymous by construction — that absence of
 * a 401/403 case IS the anonymous-access assertion.
 */
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false"
    })
@Testcontainers
class PortalContentIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  private static final String BASE = "/api/content";

  @Autowired private WebApplicationContext context;
  @Autowired private ContentBlockService contentBlockService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void publishedKeyReturnsItsBodyAnonymously() throws Exception {
    contentBlockService.create("landing.hero", ContentStatus.DRAFT);
    contentBlockService.upsertTranslation("landing.hero", "ru", "Добро пожаловать");
    contentBlockService.updateStatus("landing.hero", ContentStatus.PUBLISHED);

    mockMvc
        .perform(get(BASE + "/landing.hero").param("locale", "ru"))
        .andExpect(status().isOk())
        .andExpect(content().string("Добро пожаловать"));
  }

  @Test
  void draftKeyIs404NotAnError() throws Exception {
    contentBlockService.create("landing.draft-only", ContentStatus.DRAFT);
    contentBlockService.upsertTranslation("landing.draft-only", "ru", "Черновик");

    mockMvc.perform(get(BASE + "/landing.draft-only")).andExpect(status().isNotFound());
  }

  @Test
  void absentKeyIs404NotAnError() throws Exception {
    mockMvc.perform(get(BASE + "/landing.does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void unknownLocaleFallsBackToEnInsteadOfErroring() throws Exception {
    contentBlockService.create("landing.locale-fallback", ContentStatus.DRAFT);
    contentBlockService.upsertTranslation("landing.locale-fallback", "en", "Default");
    contentBlockService.updateStatus("landing.locale-fallback", ContentStatus.PUBLISHED);

    mockMvc
        .perform(get(BASE + "/landing.locale-fallback").param("locale", "fr"))
        .andExpect(status().isOk())
        .andExpect(content().string("Default"));
  }
}
