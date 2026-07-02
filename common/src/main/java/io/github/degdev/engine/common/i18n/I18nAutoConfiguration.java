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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Static-message i18n, always on. Contributes the engine {@link MessageSource} over {@code
 * classpath:messages} and the framework-agnostic {@link LocaleResolver}. Neither has an external
 * requirement, so this mounts in every host (including the stateless admin host) as soon as {@code
 * common} is on the classpath — the seam through which the {@code ?locale=} contract is served.
 * Registered from {@code META-INF/spring/…AutoConfiguration.imports}.
 *
 * <p>Ordered {@code before} {@link MessageSourceAutoConfiguration} so the engine's message source
 * (UTF-8, base-bundle fallback) wins and Boot's default backs off via its
 * {@code @ConditionalOnMissingBean}. The locale-resolver bean is named {@code engineLocaleResolver}
 * to avoid colliding with the servlet {@code localeResolver} bean Spring MVC contributes in web
 * hosts.
 */
@AutoConfiguration(before = MessageSourceAutoConfiguration.class)
public class I18nAutoConfiguration {

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

  /**
   * The framework-agnostic request-locale resolver for the {@code ?locale=} contract.
   *
   * @return the locale resolver
   */
  @Bean
  public LocaleResolver engineLocaleResolver() {
    return new LocaleResolver();
  }
}
