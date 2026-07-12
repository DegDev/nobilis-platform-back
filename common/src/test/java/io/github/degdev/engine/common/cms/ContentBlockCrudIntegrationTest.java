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
package io.github.degdev.engine.common.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.degdev.engine.common.i18n.UnsupportedLocaleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers), proving the DoD through {@link
 * ContentBlockService} rather than raw JPA (that round-trip is already covered by {@link
 * ContentBlockFlywayIntegrationTest}): {@link ContentBlockService} is autowired only because the
 * EMF is present (the stateless-host absence case is covered by {@link
 * ContentBlockAutoConfigurationTest}); CRUD + translation upsert/remove round-trip; the public read
 * path resolves en/ru/ro and falls back to en; a DRAFT block is excluded from the public read but
 * visible via the admin list; duplicate key / unknown block / unknown locale are rejected. {@code
 * ddl-auto=validate} additionally proves entity↔migration parity.
 */
@SpringBootTest
@Testcontainers
class ContentBlockCrudIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private ContentBlockService service;

  @Test
  void fullRoundTripThroughTheService() {
    service.create("home.hero", ContentStatus.DRAFT);
    service.upsertTranslation("home.hero", "en", "Hello");
    service.upsertTranslation("home.hero", "ru", "Привет");
    service.upsertTranslation("home.hero", "ro", "Salut");

    // DRAFT: not visible on the public read path, visible on the admin list.
    assertThat(service.readPublished("home.hero", "ru")).isEmpty();
    assertThat(service.list(org.springframework.data.domain.Pageable.unpaged()).getContent())
        .extracting(ContentBlock::getKey)
        .contains("home.hero");

    service.updateStatus("home.hero", ContentStatus.PUBLISHED);

    assertThat(service.readPublished("home.hero", "ro")).contains("Salut");
    assertThat(service.readPublished("home.hero", "unsupported-xx")).contains("Hello");

    service.removeTranslation("home.hero", "ro");
    assertThat(service.find("home.hero").orElseThrow().getTranslations())
        .extracting(ContentTranslation::getLocale)
        .containsExactlyInAnyOrder("en", "ru");

    assertThat(service.delete("home.hero")).isTrue();
    assertThat(service.find("home.hero")).isEmpty();
  }

  @Test
  void createRejectsADuplicateKey() {
    service.create("home.footer", ContentStatus.DRAFT);

    assertThatThrownBy(() -> service.create("home.footer", ContentStatus.DRAFT))
        .isInstanceOf(ContentBlockConflictException.class);
  }

  @Test
  void translationOperationsRejectAnUnknownBlockOrLocale() {
    assertThatThrownBy(() -> service.upsertTranslation("absent", "ru", "text"))
        .isInstanceOf(ContentBlockNotFoundException.class);

    service.create("home.banner", ContentStatus.DRAFT);
    assertThatThrownBy(() -> service.upsertTranslation("home.banner", "fr", "text"))
        .isInstanceOf(UnsupportedLocaleException.class);
    assertThatThrownBy(() -> service.removeTranslation("home.banner", "ru"))
        .isInstanceOf(ContentBlockNotFoundException.class);
  }
}
