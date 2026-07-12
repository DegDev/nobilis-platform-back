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

/**
 * Signals that a translation write (upsert or remove) named a blank or unsupported locale code.
 * Shared across features (CMS, notifications) — only the write path rejects this; the public read
 * path never errors, it falls back to {@code ru} per {@link LocaleResolver}'s contract. A domain
 * signal, deliberately free of any HTTP/web type; the admin layer maps it to an RFC 9457 {@code 400
 * Bad Request}.
 */
public class UnsupportedLocaleException extends RuntimeException implements MessageKeyException {

  private final String locale;

  /**
   * Creates the exception.
   *
   * @param locale the rejected locale code, as submitted (may be blank or {@code null})
   */
  public UnsupportedLocaleException(String locale) {
    super("Unsupported locale: '" + locale + "'");
    this.locale = locale;
  }

  @Override
  public String messageKey() {
    return "error.unsupported-locale";
  }

  @Override
  public Object[] messageArguments() {
    return new Object[] {locale};
  }
}
