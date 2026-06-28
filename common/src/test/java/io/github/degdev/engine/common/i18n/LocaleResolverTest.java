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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocaleResolverTest {

  private final LocaleResolver resolver = new LocaleResolver();

  @Test
  void defaultLocaleIsRussian() {
    assertThat(LocaleResolver.DEFAULT_LOCALE).isEqualTo(Locale.forLanguageTag("ru"));
  }

  @Test
  void resolvesSupportedRomanian() {
    assertThat(resolver.resolve("ro")).isEqualTo(Locale.forLanguageTag("ro"));
  }

  @Test
  void resolvesSupportedRussian() {
    assertThat(resolver.resolve("ru")).isEqualTo(Locale.forLanguageTag("ru"));
  }

  @Test
  void normalisesCaseAndWhitespace() {
    assertThat(resolver.resolve("  RO  ")).isEqualTo(Locale.forLanguageTag("ro"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "en", "de", "xx", "russian"})
  void fallsBackToDefaultForAbsentOrUnsupported(String requested) {
    assertThat(resolver.resolve(requested)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
  }
}
