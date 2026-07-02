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
package io.github.degdev.engine.common.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing, mounted only when JPA is actually active. {@code common} puts Spring Data JPA on
 * the classpath, but a stateless host (e.g. the milestone-03 admin slice) excludes the
 * DataSource/JPA auto-configuration, so there is no {@link EntityManagerFactory} — and then
 * auditing must NOT mount (its {@code @EnableJpaAuditing} handler needs the JPA metamodel and would
 * fail without an EMF).
 *
 * <p>The guard is {@link ConditionalOnBean}({@code EntityManagerFactory}), NOT
 * {@code @ConditionalOnClass} — the JPA <em>classes</em> are always present via {@code common}'s
 * compile dependency; only the EMF <em>bean</em> distinguishes a real JPA host from the stateless
 * one. The config is ordered {@code after} {@link HibernateJpaAutoConfiguration} so the EMF exists
 * when the condition is evaluated. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing(auditorAwareRef = "systemAuditorAware")
public class PersistenceAutoConfiguration {

  /**
   * The {@code system} auditor stub (no authenticated principal yet). The bean name matches
   * {@code @EnableJpaAuditing(auditorAwareRef = "systemAuditorAware")}.
   *
   * @return the system auditor
   */
  @Bean
  public SystemAuditorAware systemAuditorAware() {
    return new SystemAuditorAware();
  }
}
