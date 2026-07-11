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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.common.i18n.LocaleResolver;
import io.github.degdev.engine.common.i18n.UnsupportedLocaleException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit-level: a mocked repository with a real {@link LocaleResolver} proves the REJECT invariants
 * (duplicate key, unknown block/translation, unsupported locale) and the public read path's
 * ru-fallback and DRAFT-exclusion behavior.
 */
class ContentBlockServiceTest {

  private ContentBlockRepository repository;
  private ContentBlockService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ContentBlockRepository.class);
    service = new ContentBlockService(repository, new LocaleResolver());
    when(repository.save(any(ContentBlock.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createRejectsADuplicateKey() {
    when(repository.findByKey("home.hero"))
        .thenReturn(Optional.of(new ContentBlock("home.hero", ContentStatus.DRAFT)));

    assertThatThrownBy(() -> service.create("home.hero", ContentStatus.DRAFT))
        .isInstanceOf(ContentBlockConflictException.class);
  }

  @Test
  void createPersistsANewBlock() {
    when(repository.findByKey("home.hero")).thenReturn(Optional.empty());

    ContentBlock created = service.create("home.hero", ContentStatus.DRAFT);

    assertThat(created.getKey()).isEqualTo("home.hero");
    assertThat(created.getStatus()).isEqualTo(ContentStatus.DRAFT);
  }

  @Test
  void updateStatusRejectsAnUnknownKey() {
    when(repository.findByKey("absent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateStatus("absent", ContentStatus.PUBLISHED))
        .isInstanceOf(ContentBlockNotFoundException.class);
  }

  @Test
  void updateStatusChangesAnExistingBlock() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    ContentBlock updated = service.updateStatus("home.hero", ContentStatus.PUBLISHED);

    assertThat(updated.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
  }

  @Test
  void upsertTranslationRejectsAnUnknownBlock() {
    when(repository.findByKey("absent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.upsertTranslation("absent", "ru", "text"))
        .isInstanceOf(ContentBlockNotFoundException.class);
  }

  @Test
  void upsertTranslationRejectsABlankLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThatThrownBy(() -> service.upsertTranslation("home.hero", "  ", "text"))
        .isInstanceOf(UnsupportedLocaleException.class);
  }

  @Test
  void upsertTranslationRejectsAnUnsupportedLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThatThrownBy(() -> service.upsertTranslation("home.hero", "fr", "text"))
        .isInstanceOf(UnsupportedLocaleException.class);
  }

  @Test
  void upsertTranslationAddsANewLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    ContentBlock updated = service.upsertTranslation("home.hero", " RU ", "Привет");

    assertThat(updated.getTranslations())
        .extracting(ContentTranslation::getLocale, ContentTranslation::getBody)
        .containsExactly(tuple("ru", "Привет"));
  }

  @Test
  void upsertTranslationReplacesAnExistingLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    block.addTranslation(new ContentTranslation("ru", "Старый"));
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    ContentBlock updated = service.upsertTranslation("home.hero", "ru", "Новый");

    assertThat(updated.getTranslations())
        .extracting(ContentTranslation::getBody)
        .containsExactly("Новый");
  }

  @Test
  void removeTranslationRejectsAnUnknownTranslation() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThatThrownBy(() -> service.removeTranslation("home.hero", "ru"))
        .isInstanceOf(ContentBlockNotFoundException.class);
  }

  @Test
  void removeTranslationDeletesAnExistingLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    block.addTranslation(new ContentTranslation("ru", "Привет"));
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    ContentBlock updated = service.removeTranslation("home.hero", "ru");

    assertThat(updated.getTranslations()).isEmpty();
  }

  @Test
  void deleteReportsWhetherABlockExisted() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));
    when(repository.findByKey("absent")).thenReturn(Optional.empty());

    assertThat(service.delete("home.hero")).isTrue();
    assertThat(service.delete("absent")).isFalse();
  }

  @Test
  void readPublishedReturnsTheExactLocale() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.PUBLISHED);
    block.addTranslation(new ContentTranslation("ru", "Привет"));
    block.addTranslation(new ContentTranslation("ro", "Salut"));
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThat(service.readPublished("home.hero", "ro")).contains("Salut");
  }

  @Test
  void readPublishedFallsBackToRuWhenTheRequestedLocaleIsMissing() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.PUBLISHED);
    block.addTranslation(new ContentTranslation("ru", "Привет"));
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThat(service.readPublished("home.hero", "ro")).contains("Привет");
    assertThat(service.readPublished("home.hero", "unsupported-xx")).contains("Привет");
    assertThat(service.readPublished("home.hero", null)).contains("Привет");
  }

  @Test
  void readPublishedExcludesADraftBlock() {
    ContentBlock block = new ContentBlock("home.hero", ContentStatus.DRAFT);
    block.addTranslation(new ContentTranslation("ru", "Привет"));
    when(repository.findByKey("home.hero")).thenReturn(Optional.of(block));

    assertThat(service.readPublished("home.hero", "ru")).isEmpty();
  }

  @Test
  void readPublishedReturnsEmptyForAnUnknownKey() {
    when(repository.findByKey("absent")).thenReturn(Optional.empty());

    assertThat(service.readPublished("absent", "ru")).isEmpty();
  }
}
