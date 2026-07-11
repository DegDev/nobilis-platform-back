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
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the notifications feature. No new {@code @AutoConfigurationPackage} is needed: the
 * repositories live under {@code io.github.degdev.engine.common}, the same base package {@code
 * SettingsAutoConfiguration} already registers.
 *
 * <p>The {@link NotificationsService} bean is method-gated: {@link ConditionalOnBean} on a JPA
 * {@link EntityManagerFactory} only. A stateless host gets no service, never a fail-fast boot.
 * Ordered {@code after} {@link HibernateJpaAutoConfiguration}. Registered from {@code
 * META-INF/spring/AutoConfiguration.imports}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
public class NotificationsAutoConfiguration {

  @Bean
  @ConditionalOnBean(EntityManagerFactory.class)
  public NotificationsService notificationsService(
      NotificationTypeRepository typeRepository,
      NotificationTemplateRepository templateRepository,
      LocaleResolver localeResolver) {
    return new NotificationsService(typeRepository, templateRepository, localeResolver);
  }
}
