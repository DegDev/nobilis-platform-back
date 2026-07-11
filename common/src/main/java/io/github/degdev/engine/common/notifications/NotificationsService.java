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
package io.github.degdev.engine.common.notifications;

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
 * Admin management of {@link NotificationType}s and their {@link NotificationTemplate}s (with
 * per-locale {@link NotificationTemplateTranslation}s). This is the config/data layer only; actual
 * dispatch is milestone 04 (the integration worker).
 *
 * <p>Write operations REJECT rather than silently correct: a duplicate type {@code key} on create
 * or a duplicate {@code (type, transport)} template throws {@link NotificationConflictException};
 * an operation naming an unknown type/template/translation throws {@link
 * NotificationTypeNotFoundException}; a translation write naming a blank/unsupported locale throws
 * {@link UnsupportedLocaleException}. Mirrors the CMS service's reject-not-correct discipline.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@link
 * NotificationsAutoConfiguration}, gated on JPA being active, so a stateless host simply has no
 * notifications service. Lombok {@code @RequiredArgsConstructor} wires the {@code final}
 * collaborators; {@code @Slf4j} provides {@code log}.
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationsService {

  private final NotificationTypeRepository typeRepository;
  private final NotificationTemplateRepository templateRepository;
  private final LocaleResolver localeResolver;

  // ──────────────────────────────────────────────────────────────────────
  // NotificationType CRUD
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Creates a new notification type.
   *
   * @param key the unique, immutable natural key
   * @param enabled whether the dispatcher should consider this type active
   * @param description an optional human-readable note (may be {@code null})
   * @return the persisted type
   * @throws NotificationConflictException if a type already exists with the same key
   */
  @Transactional
  public NotificationType createType(String key, boolean enabled, String description) {
    if (typeRepository.findByKey(key).isPresent()) {
      throw new NotificationConflictException("Notification type key '" + key + "' already exists");
    }
    log.debug(
        "Creating notification type '{}' (enabled={}, description={})", key, enabled, description);
    return typeRepository.save(new NotificationType(key, enabled, description));
  }

  /**
   * Reads one notification type by key.
   *
   * @param key the notification type key
   * @return the type, or empty if none exists for {@code key}
   */
  @Transactional(readOnly = true)
  public Optional<NotificationType> findType(String key) {
    return typeRepository.findByKey(key);
  }

  /**
   * Lists notification types, one page at a time.
   *
   * @param pageable the page request (page number, size, sort)
   * @return the requested page of types
   */
  @Transactional(readOnly = true)
  public Page<NotificationType> listTypes(Pageable pageable) {
    return typeRepository.findAll(pageable);
  }

  /**
   * Updates a type's enabled flag and/or description.
   *
   * @param key the notification type key
   * @param enabled the new enabled state
   * @param description the new description (may be {@code null})
   * @return the updated type
   * @throws NotificationTypeNotFoundException if no type exists for {@code key}
   */
  @Transactional
  public NotificationType updateType(String key, boolean enabled, String description) {
    NotificationType type = requireType(key);
    type.setEnabled(enabled);
    type.setDescription(description);
    log.debug("Updated notification type '{}'", key);
    return typeRepository.save(type);
  }

  /**
   * Deletes a notification type and all its templates/translations (cascade via the ORM; the DB
   * also has {@code ON DELETE CASCADE} on the FKs).
   *
   * @param key the notification type key
   * @return {@code true} if a type was deleted, {@code false} if the key was already unset
   */
  @Transactional
  public boolean deleteType(String key) {
    return typeRepository
        .findByKey(key)
        .map(
            type -> {
              typeRepository.delete(type);
              log.debug("Deleted notification type '{}'", key);
              return true;
            })
        .orElse(false);
  }

  // ──────────────────────────────────────────────────────────────────────
  // NotificationTemplate CRUD
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Creates a new, translation-less template for a type + transport pair.
   *
   * @param typeKey the owning notification type's key
   * @param transport the transport channel
   * @return the persisted template (with no translations yet)
   * @throws NotificationTypeNotFoundException if no type exists for {@code typeKey}
   * @throws NotificationConflictException if a template already exists for the same (type,
   *     transport) pair
   */
  @Transactional
  public NotificationTemplate createTemplate(String typeKey, Transport transport) {
    NotificationType type = requireType(typeKey);
    if (templateRepository.findByTypeIdAndTransport(type.getId(), transport).isPresent()) {
      throw new NotificationConflictException(
          "Template for type '" + typeKey + "' + transport '" + transport + "' already exists");
    }
    log.debug("Creating notification template for type '{}' + transport '{}'", typeKey, transport);
    return templateRepository.save(new NotificationTemplate(type, transport));
  }

  /**
   * Lists all templates, one page at a time. Each template's translations are force-loaded in-tx.
   *
   * @param pageable the page request
   * @return the requested page of templates, each with translations loaded
   */
  @Transactional(readOnly = true)
  public Page<NotificationTemplate> listTemplates(Pageable pageable) {
    return templateRepository.findAll(pageable).map(NotificationsService::withTranslationsLoaded);
  }

  /**
   * Lists templates for a specific notification type. Each template's translations are force-loaded
   * in-tx.
   *
   * @param typeKey the notification type key
   * @param pageable the page request
   * @return the requested page of templates for the type, each with translations loaded
   * @throws NotificationTypeNotFoundException if no type exists for {@code typeKey}
   */
  @Transactional(readOnly = true)
  public Page<NotificationTemplate> listTemplatesByType(String typeKey, Pageable pageable) {
    NotificationType type = requireType(typeKey);
    return templateRepository
        .findByTypeId(type.getId(), pageable)
        .map(NotificationsService::withTranslationsLoaded);
  }

  /**
   * Reads one template by type key + transport, with translations loaded.
   *
   * @param typeKey the notification type key
   * @param transport the transport channel
   * @return the template, or empty if none exists for the pair
   * @throws NotificationTypeNotFoundException if no type exists for {@code typeKey}
   */
  @Transactional(readOnly = true)
  public Optional<NotificationTemplate> findTemplate(String typeKey, Transport transport) {
    NotificationType type = requireType(typeKey);
    return templateRepository
        .findByTypeIdAndTransport(type.getId(), transport)
        .map(NotificationsService::withTranslationsLoaded);
  }

  /**
   * Deletes a template and its translations (cascade).
   *
   * @param typeKey the notification type key
   * @param transport the transport channel
   * @return {@code true} if a template was deleted, {@code false} if the pair was already unset
   * @throws NotificationTypeNotFoundException if no type exists for {@code typeKey}
   */
  @Transactional
  public boolean deleteTemplate(String typeKey, Transport transport) {
    NotificationType type = requireType(typeKey);
    return templateRepository
        .findByTypeIdAndTransport(type.getId(), transport)
        .map(
            template -> {
              templateRepository.delete(template);
              log.debug(
                  "Deleted notification template for type '{}' + transport '{}'",
                  typeKey,
                  transport);
              return true;
            })
        .orElse(false);
  }

  // ──────────────────────────────────────────────────────────────────────
  // NotificationTemplateTranslation CRUD
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Creates or replaces a template's translation for one locale.
   *
   * @param typeKey the notification type key
   * @param transport the transport channel
   * @param locale the locale code (e.g. {@code ru}, {@code ro}); trimmed and lower-cased
   * @param subject the localized subject (may be {@code null} for body-only transports)
   * @param body the localized body
   * @return the updated template, with translations loaded
   * @throws NotificationTypeNotFoundException if no type/template exists for the pair
   * @throws UnsupportedLocaleException if {@code locale} is blank or not one the engine supports
   */
  @Transactional
  public NotificationTemplate upsertTranslation(
      String typeKey, Transport transport, String locale, String subject, String body) {
    NotificationTemplate template = requireTemplate(typeKey, transport);
    String normalizedLocale = requireSupportedLocale(locale);
    Optional<NotificationTemplateTranslation> existing = translationFor(template, normalizedLocale);
    if (existing.isPresent()) {
      existing.get().setSubject(subject);
      existing.get().setBody(body);
    } else {
      template.addTranslation(new NotificationTemplateTranslation(normalizedLocale, subject, body));
    }
    log.debug(
        "Upserted '{}' translation for template (type '{}', transport '{}')",
        normalizedLocale,
        typeKey,
        transport);
    return templateRepository.save(template);
  }

  /**
   * Removes a template's translation for one locale.
   *
   * @param typeKey the notification type key
   * @param transport the transport channel
   * @param locale the locale code to remove
   * @return the updated template, with translations loaded
   * @throws NotificationTypeNotFoundException if no type/template/translation exists
   * @throws UnsupportedLocaleException if {@code locale} is blank or not one the engine supports
   */
  @Transactional
  public NotificationTemplate removeTranslation(
      String typeKey, Transport transport, String locale) {
    NotificationTemplate template = requireTemplate(typeKey, transport);
    String normalizedLocale = requireSupportedLocale(locale);
    boolean removed =
        template.getTranslations().removeIf(t -> t.getLocale().equals(normalizedLocale));
    if (!removed) {
      throw new NotificationTypeNotFoundException(
          "Template (type '"
              + typeKey
              + "', transport '"
              + transport
              + "') has no '"
              + normalizedLocale
              + "' translation");
    }
    log.debug(
        "Removed '{}' translation from template (type '{}', transport '{}')",
        normalizedLocale,
        typeKey,
        transport);
    return templateRepository.save(template);
  }

  // ──────────────────────────────────────────────────────────────────────
  // Dispatch read path (milestone 04)
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Dispatch read path: the translation to send for a type + transport + locale, resolving the
   * exact locale then falling back to {@code ru} per {@link LocaleResolver}'s contract, same
   * fallback shape as {@code ContentBlockService#readPublished}. Never errors on bad input; a
   * disabled type, an absent type/template, or no matching translation simply yields no result.
   *
   * @param typeKey the notification type key
   * @param transport the transport channel
   * @param locale the raw requested locale code; may be blank/null
   * @return the resolved translation, or empty if the type is disabled/absent, the template is
   *     absent, or it has no translation in the resolved locale nor the {@code ru} fallback
   */
  @Transactional(readOnly = true)
  public Optional<NotificationTemplateTranslation> resolveForDispatch(
      String typeKey, Transport transport, String locale) {
    return typeRepository
        .findByKey(typeKey)
        .filter(NotificationType::isEnabled)
        .flatMap(
            type ->
                templateRepository
                    .findByTypeIdAndTransport(type.getId(), transport)
                    .map(NotificationsService::withTranslationsLoaded))
        .flatMap(template -> resolveTranslation(template, locale));
  }

  private Optional<NotificationTemplateTranslation> resolveTranslation(
      NotificationTemplate template, String locale) {
    String resolvedTag = localeResolver.resolve(locale).toLanguageTag();
    return translationFor(template, resolvedTag)
        .or(() -> translationFor(template, LocaleResolver.DEFAULT_LOCALE.toLanguageTag()));
  }

  // ──────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────

  private NotificationType requireType(String key) {
    return typeRepository
        .findByKey(key)
        .orElseThrow(
            () ->
                new NotificationTypeNotFoundException(
                    "Notification type '" + key + "' does not exist"));
  }

  private NotificationTemplate requireTemplate(String typeKey, Transport transport) {
    NotificationType type = requireType(typeKey);
    return templateRepository
        .findByTypeIdAndTransport(type.getId(), transport)
        .map(NotificationsService::withTranslationsLoaded)
        .orElseThrow(
            () ->
                new NotificationTypeNotFoundException(
                    "Template for type '"
                        + typeKey
                        + "' + transport '"
                        + transport
                        + "' does not exist"));
  }

  private static Optional<NotificationTemplateTranslation> translationFor(
      NotificationTemplate template, String locale) {
    return template.getTranslations().stream()
        .filter(t -> t.getLocale().equals(locale))
        .findFirst();
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
   * Forces the lazy {@code type} {@code @ManyToOne} and {@code translations} {@code @OneToMany} to
   * load inside the transaction so the caller can use them after the entity detaches (open-in-view
   * is off).
   */
  private static NotificationTemplate withTranslationsLoaded(NotificationTemplate template) {
    template.getType().getKey();
    template.getTranslations().size();
    return template;
  }
}
