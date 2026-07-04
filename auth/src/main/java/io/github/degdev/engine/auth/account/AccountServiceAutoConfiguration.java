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
package io.github.degdev.engine.auth.account;

import io.github.degdev.engine.auth.role.RoleRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Contributes the {@link AccountService} bean when JPA is active, mirroring {@code
 * RoleServiceAutoConfiguration} exactly.
 *
 * <p>Gated on {@link ConditionalOnBean}({@code EntityManagerFactory}), NOT on the repositories
 * directly: {@code @ConditionalOnBean} only sees bean definitions already contributed, and the
 * repository beans are registered by the JPA-repositories auto-configuration, whose ordering
 * relative to this class is not guaranteed — whereas ordering {@code after
 * HibernateJpaAutoConfiguration} makes the {@code EntityManagerFactory} reliably present at
 * condition time. The repositories are then injected into the {@code @Bean} method, resolved at
 * instantiation when they certainly exist. A stateless host has no {@code EntityManagerFactory}, so
 * it gets no account service — and therefore no accounts controller (see {@code
 * AccountAdminAutoConfiguration}). Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
public class AccountServiceAutoConfiguration {

  /**
   * Provides the account management service when JPA is active.
   *
   * @param accountRepository the account repository
   * @param identityRepository the account-identity repository (for the read-model's provider view)
   * @param roleRepository the role repository (to resolve assigned role ids)
   * @return the account service
   */
  @Bean
  public AccountService accountService(
      AccountRepository accountRepository,
      AccountIdentityRepository identityRepository,
      RoleRepository roleRepository) {
    return new AccountService(accountRepository, identityRepository, roleRepository);
  }
}
