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

import io.github.degdev.engine.common.i18n.LocaleResolver;
import io.github.degdev.engine.common.i18n.UnsupportedLocaleException;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of {@link ContentBlock}s and their {@link ContentTranslation}s, plus the public
 * read path that resolves a published body for a requested locale.
 *
 * <p>Write operations REJECT rather than silently correct: a duplicate {@code key} on create throws
 * {@link ContentBlockConflictException}; an operation naming an unknown block or translation throws
 * {@link ContentBlockNotFoundException}; upserting or removing a translation with a
 * blank/unsupported locale throws {@link
 * io.github.degdev.engine.common.i18n.UnsupportedLocaleException}. The public read path ({@link
 * #readPublished}) is the opposite by design — it never errors, falling back to {@code ru} per
 * {@link LocaleResolver}'s locked contract, and only ever sees {@link ContentStatus#PUBLISHED}
 * blocks.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * ContentBlockAutoConfiguration}, gated on JPA being active, so a stateless host simply has no CMS
 * service. Lombok {@code @RequiredArgsConstructor} wires the {@code final} collaborators;
 * {@code @Slf4j} provides {@code log}.
 */
@Slf4j
@RequiredArgsConstructor
public class ContentBlockService {

  private final ContentBlockRepository repository;
  private final LocaleResolver localeResolver;

  /**
   * Creates a new, translation-less content block.
   *
   * @param key the unique, immutable natural key
   * @param status the initial publication status
   * @return the persisted block
   * @throws ContentBlockConflictException if a block already exists with the same key
   */
  @Transactional
  public ContentBlock create(String key, ContentStatus status) {
    if (repository.findByKey(key).isPresent()) {
      throw new ContentBlockConflictException("Content block key '" + key + "' already exists");
    }
    log.debug("Creating content block '{}' ({})", key, status);
    return repository.save(new ContentBlock(key, status));
  }

  /**
   * Reads one content block by key, with its translations loaded, for admin display (any status).
   *
   * @param key the content block key
   * @return the block, or empty if none exists for {@code key}
   */
  @Transactional(readOnly = true)
  public Optional<ContentBlock> find(String key) {
    return repository.findByKey(key).map(ContentBlockService::withTranslationsLoaded);
  }

  /**
   * Lists content blocks for admin display (any status), one page at a time.
   *
   * @param pageable the page request (page number, size, sort)
   * @return the requested page of blocks, each with its translations loaded
   */
  @Transactional(readOnly = true)
  public Page<ContentBlock> list(Pageable pageable) {
    return repository.findAll(pageable).map(ContentBlockService::withTranslationsLoaded);
  }

  /**
   * Updates a block's publication status.
   *
   * @param key the content block key
   * @param status the new status
   * @return the updated block, with its translations loaded
   * @throws ContentBlockNotFoundException if no block exists for {@code key}
   */
  @Transactional
  public ContentBlock updateStatus(String key, ContentStatus status) {
    ContentBlock block = requireBlock(key);
    block.setStatus(status);
    log.debug("Updated content block '{}' status to {}", key, status);
    return repository.save(block);
  }

  /**
   * Creates or replaces a block's translation for one locale.
   *
   * @param key the content block key
   * @param locale the locale code (e.g. {@code ru}, {@code ro}); trimmed and lower-cased
   * @param body the translated content
   * @return the updated block, with its translations loaded
   * @throws ContentBlockNotFoundException if no block exists for {@code key}
   * @throws UnsupportedLocaleException if {@code locale} is blank or not one the engine supports
   */
  @Transactional
  public ContentBlock upsertTranslation(String key, String locale, String body) {
    ContentBlock block = requireBlock(key);
    String normalizedLocale = requireSupportedLocale(locale);
    Optional<ContentTranslation> existing = translationFor(block, normalizedLocale);
    if (existing.isPresent()) {
      existing.get().setBody(body);
    } else {
      block.addTranslation(new ContentTranslation(normalizedLocale, body));
    }
    log.debug("Upserted '{}' translation for content block '{}'", normalizedLocale, key);
    return repository.save(block);
  }

  /**
   * Removes a block's translation for one locale.
   *
   * @param key the content block key
   * @param locale the locale code to remove
   * @return the updated block, with its translations loaded
   * @throws ContentBlockNotFoundException if no block exists for {@code key}, or it has no
   *     translation for {@code locale}
   * @throws UnsupportedLocaleException if {@code locale} is blank or not one the engine supports
   */
  @Transactional
  public ContentBlock removeTranslation(String key, String locale) {
    ContentBlock block = requireBlock(key);
    String normalizedLocale = requireSupportedLocale(locale);
    boolean removed = block.getTranslations().removeIf(t -> t.getLocale().equals(normalizedLocale));
    if (!removed) {
      throw new ContentBlockNotFoundException(
          "Content block '" + key + "' has no '" + normalizedLocale + "' translation");
    }
    log.debug("Removed '{}' translation from content block '{}'", normalizedLocale, key);
    return repository.save(block);
  }

  /**
   * Deletes a content block and its translations (cascade).
   *
   * @param key the content block key
   * @return {@code true} if a block was deleted, {@code false} if the key was already unset
   */
  @Transactional
  public boolean delete(String key) {
    return repository
        .findByKey(key)
        .map(
            block -> {
              repository.delete(block);
              log.debug("Deleted content block '{}'", key);
              return true;
            })
        .orElse(false);
  }

  /**
   * Public read path: the {@link ContentStatus#PUBLISHED} body for {@code key} in the requested
   * locale, falling back to {@code ru} per {@link LocaleResolver}'s contract when the exact locale
   * is missing or unsupported. Never errors on bad input; a DRAFT block or an absent key/body
   * simply yields no result.
   *
   * @param key the content block key
   * @param locale the raw requested locale code (e.g. from {@code ?locale=}); may be blank/null
   * @return the resolved body, or empty if the block is absent, not PUBLISHED, or has no body in
   *     the resolved locale nor the {@code ru} fallback
   */
  @Transactional(readOnly = true)
  public Optional<String> readPublished(String key, String locale) {
    return repository
        .findByKey(key)
        .filter(block -> block.getStatus() == ContentStatus.PUBLISHED)
        .flatMap(block -> resolveBody(block, locale));
  }

  private Optional<String> resolveBody(ContentBlock block, String locale) {
    String resolvedTag = localeResolver.resolve(locale).toLanguageTag();
    return translationFor(block, resolvedTag)
        .or(() -> translationFor(block, LocaleResolver.DEFAULT_LOCALE.toLanguageTag()))
        .map(ContentTranslation::getBody);
  }

  private static Optional<ContentTranslation> translationFor(ContentBlock block, String locale) {
    return block.getTranslations().stream().filter(t -> t.getLocale().equals(locale)).findFirst();
  }

  private ContentBlock requireBlock(String key) {
    return repository
        .findByKey(key)
        .map(ContentBlockService::withTranslationsLoaded)
        .orElseThrow(
            () -> new ContentBlockNotFoundException("Content block '" + key + "' does not exist"));
  }

  /** Rejects a blank or unsupported locale code; otherwise returns it trimmed and lower-cased. */
  private String requireSupportedLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      throw new UnsupportedLocaleException(locale);
    }
    String normalized = locale.trim().toLowerCase(Locale.ROOT);
    if (!localeResolver.resolve(normalized).toLanguageTag().equals(normalized)) {
      throw new UnsupportedLocaleException(locale);
    }
    return normalized;
  }

  /**
   * Forces the lazy {@code translations} {@code @OneToMany} to load inside the transaction so the
   * caller can use it after the entity detaches (open-in-view is off).
   */
  private static ContentBlock withTranslationsLoaded(ContentBlock block) {
    block.getTranslations().size();
    return block;
  }
}
