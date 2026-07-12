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
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the CMS content-block service. No new {@code @AutoConfigurationPackage} is needed here:
 * {@link ContentBlockRepository} lives under {@code io.github.degdev.engine.common}, the same base
 * package {@code SettingsAutoConfiguration} already registers, so Spring Data's repository scan
 * picks it up without a second, colliding registration (same reasoning as {@code
 * SettingsAutoConfiguration}, see its Javadoc).
 *
 * <p>The {@link ContentBlockService} bean is method-gated: {@link ConditionalOnBean} on a JPA
 * {@link EntityManagerFactory} only — unlike settings, CMS content is never encrypted, so there is
 * no {@code CryptoService} dependency to gate on. A stateless host gets the package registration
 * (harmless, nothing scans it) but no service, never a fail-fast boot. Ordered {@code after} {@link
 * HibernateJpaAutoConfiguration} so the EMF is registered before this condition is evaluated.
 * {@link LocaleResolver} is not part of the gate: {@code common}'s {@code I18nAutoConfiguration}
 * mounts it unconditionally in every host, including this one. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
public class ContentBlockAutoConfiguration {

  /**
   * Provides the CMS content-block service when JPA is active.
   *
   * @param repository the content block repository (scanned via {@code SettingsAutoConfiguration}'s
   *     auto-configuration package)
   * @param localeResolver the engine's locale resolver, for the public read path's en-fallback
   * @return the content block service
   */
  @Bean
  @ConditionalOnBean(EntityManagerFactory.class)
  public ContentBlockService contentBlockService(
      ContentBlockRepository repository, LocaleResolver localeResolver) {
    return new ContentBlockService(repository, localeResolver);
  }
}
