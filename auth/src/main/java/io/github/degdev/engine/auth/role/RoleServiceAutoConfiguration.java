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
package io.github.degdev.engine.auth.role;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Contributes the {@link RoleService} bean when JPA is active, mirroring how {@code common}'s
 * {@code SettingsAutoConfiguration} contributes {@code SettingsService}.
 *
 * <p>Gated on {@link ConditionalOnBean}({@code EntityManagerFactory}), NOT on {@code
 * RoleRepository} directly: {@code @ConditionalOnBean} only sees bean definitions already
 * contributed, and the repository beans are registered by the JPA-repositories auto-configuration,
 * whose ordering relative to this class is not guaranteed — whereas ordering {@code after
 * HibernateJpaAutoConfiguration} makes the {@code EntityManagerFactory} bean reliably present at
 * condition time (the same choice {@code SettingsAutoConfiguration} makes). The repository is then
 * injected into the {@code @Bean} method, resolved at instantiation when it certainly exists. A
 * stateless host has no {@code EntityManagerFactory}, so it gets no role service — and therefore no
 * roles controller (see {@code RoleAdminAutoConfiguration}). Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
public class RoleServiceAutoConfiguration {

  /**
   * Provides the role management service when JPA is active.
   *
   * @param roleRepository the role repository (scanned via {@code
   *     AuthPersistenceAutoConfiguration})
   * @return the role service
   */
  @Bean
  public RoleService roleService(RoleRepository roleRepository) {
    return new RoleService(roleRepository);
  }
}
