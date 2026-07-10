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
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration slice against a real PostgreSQL 18 (Testcontainers). Proves the milestone DoD: Flyway
 * applies {@code V5} so a {@link ContentBlock} with two {@link ContentTranslation}s round-trips,
 * and {@code orphanRemoval} deletes a translation row once it is removed from the collection.
 * {@code ddl-auto=validate} additionally proves entity↔migration parity.
 */
@SpringBootTest
@Testcontainers
class ContentBlockFlywayIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Autowired private EntityManager entityManager;

  @Test
  @Transactional
  void contentBlockWithTranslationsRoundTrips() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    block.addTranslation(new ContentTranslation("ru", "Привет"));
    block.addTranslation(new ContentTranslation("ro", "Salut"));

    entityManager.persist(block);
    entityManager.flush();
    entityManager.clear();

    ContentBlock reloaded = entityManager.find(ContentBlock.class, block.getId());

    assertThat(reloaded.getKey()).isEqualTo("home.hero");
    assertThat(reloaded.getStatus()).isEqualTo(ContentStatus.DRAFT);
    assertThat(reloaded.getTranslations())
        .extracting(ContentTranslation::getLocale, ContentTranslation::getBody)
        .containsExactlyInAnyOrder(tuple("ru", "Привет"), tuple("ro", "Salut"));
  }

  @Test
  @Transactional
  void removingATranslationFromTheCollectionDeletesItsRow() {
    ContentBlock block = new ContentBlock("home.footer", ContentStatus.DRAFT);
    block.addTranslation(new ContentTranslation("ru", "Копирайт"));
    entityManager.persist(block);
    entityManager.flush();
    entityManager.clear();

    ContentBlock reloaded = entityManager.find(ContentBlock.class, block.getId());
    Long translationId = reloaded.getTranslations().get(0).getId();
    reloaded.getTranslations().clear();
    entityManager.flush();
    entityManager.clear();

    assertThat(entityManager.find(ContentTranslation.class, translationId)).isNull();
  }
}
