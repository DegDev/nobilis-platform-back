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
import org.springframework.context.MessageSource;

class MessageSourceTest {

  private final MessageSource messageSource = new I18nAutoConfiguration().messageSource();

  @Test
  void resolvesEnglishAndPresentRussianMessages() {
    assertThat(messageSource.getMessage("error.forbidden", null, Locale.ENGLISH))
        .isEqualTo("You do not have permission to perform this action");
    assertThat(messageSource.getMessage("error.forbidden", null, Locale.forLanguageTag("ru")))
        .isEqualTo("У вас нет разрешения на выполнение этого действия");
  }

  @Test
  void missingOverlayMessageFallsBackToNativeEnglish() {
    assertThat(messageSource.getMessage("validation.failed", null, Locale.forLanguageTag("ro")))
        .isEqualTo("Validation failed");
  }
}
