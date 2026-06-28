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

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Static-message i18n. A {@link ReloadableResourceBundleMessageSource} over {@code
 * classpath:messages} resolves backend UI strings, falling back to the default {@code
 * messages.properties} bundle when a key is missing for the requested locale. The RU/RO bundles are
 * scaffolded now and populated at milestone {@code 05-i18n-static}.
 */
@Configuration
public class I18nConfig {

  /**
   * The engine {@link MessageSource} over {@code classpath:messages}. Overrides Spring Boot's
   * default so encoding is UTF-8 and a missing key falls back to the base bundle rather than the
   * host's system locale.
   *
   * @return the configured message source
   */
  @Bean
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return messageSource;
  }
}
