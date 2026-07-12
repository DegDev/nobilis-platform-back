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
package io.github.degdev.engine.common.i18n;

import java.util.Locale;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Resolves the request locale per the back↔front contract: transport is the {@code ?locale=<code>}
 * query parameter, valid values are {@code en}/{@code ru}/{@code ro} (lowercase ISO 639-1), and
 * anything absent, blank, or unsupported silently falls back to the default {@link #DEFAULT_LOCALE}
 * ({@code en}) — bad input is never an error.
 *
 * <p>Deliberately framework-agnostic (a plain component taking a {@code String}, not a Servlet
 * {@code LocaleResolver}) so {@code common} stays free of any web dependency. The web modules bind
 * the query parameter to {@link #resolve(String)} when the first real endpoint appears (milestone
 * {@code 02}/{@code 03}). Instantiated as a {@code @Bean} by {@link I18nAutoConfiguration}.
 */
public class LocaleResolver {

  /** Single source of truth for the engine default locale, mirrored by the front locale service. */
  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en");

  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ru", "ro");

  /**
   * Resolves the effective locale from a requested code, defaulting silently rather than erroring
   * so a bad or absent {@code ?locale=} value never breaks a request.
   *
   * @param requested the raw locale code (e.g. from {@code ?locale=}); may be {@code null}/blank
   * @return the matching supported locale, or {@link #DEFAULT_LOCALE} when absent or unsupported
   */
  public Locale resolve(@Nullable String requested) {
    if (requested == null) {
      return DEFAULT_LOCALE;
    }
    String code = requested.trim().toLowerCase(Locale.ROOT);
    return SUPPORTED_LANGUAGES.contains(code) ? Locale.forLanguageTag(code) : DEFAULT_LOCALE;
  }
}
