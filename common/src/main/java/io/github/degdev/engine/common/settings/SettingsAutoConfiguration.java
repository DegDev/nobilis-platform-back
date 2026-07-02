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
package io.github.degdev.engine.common.settings;

import io.github.degdev.engine.common.crypto.CryptoAutoConfiguration;
import io.github.degdev.engine.common.crypto.CryptoService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the settings feature so a host gets it by depending on {@code common} — not by
 * component-scanning {@code io.github.degdev.engine.common} (which its scan root never reaches).
 *
 * <p><b>Why {@link AutoConfigurationPackage} and not
 * {@code @EntityScan}/{@code @EnableJpaRepositories}.</b> Those would have to be processed before
 * the {@link EntityManagerFactory} is built, so they cannot be gated on it; worse, an unconditional
 * {@code @EntityScan} in a library REPLACES a host's entity packages (breaking a sibling module's
 * entities), and {@code @EnableJpaRepositories} makes Boot's own repository auto-configuration back
 * off GLOBALLY (breaking a sibling module's repositories). {@link AutoConfigurationPackage} instead
 * ADDS a package to the auto-configuration packages, which Boot's default entity scan AND Spring
 * Data repository scan both consume — additive, no replacement, no back-off. The registration is
 * unconditional (it runs during config parsing, before any EMF is built) and is simply harmless in
 * a stateless host where nothing scans it.
 *
 * <p><b>Why the module root and not this sub-package.</b> All of {@code common}'s persistent
 * artifacts live under {@code io.github.degdev.engine.common}, so one add covers them. It also
 * matters for de-duplication: {@code AutoConfigurationPackages} holds base packages in a set, so
 * registering a package a host ALREADY roots at (common's own test bootstrap roots at {@code
 * engine.common}) collapses to a single scan. Registering the {@code …settings} sub-package instead
 * would be a SECOND, distinct package nested under {@code engine.common}, and Spring Data would
 * scan {@link SettingRepository} from both and fail with a {@code BeanDefinitionOverrideException}.
 * Real hosts (admin/app) root elsewhere, so this is one clean add there regardless.
 *
 * <p>The {@link SettingsService} bean, by contrast, is method-gated: {@link ConditionalOnBean} on
 * both a JPA {@link EntityManagerFactory} (so {@link SettingRepository} exists to inject) and a
 * {@link CryptoService} (so secrets can be encrypted). A stateless or crypto-less host gets the
 * package registration but no service — never a fail-fast boot. It is ordered {@code after} BOTH
 * {@link HibernateJpaAutoConfiguration} AND {@link CryptoAutoConfiguration} for a reason: {@link
 * ConditionalOnBean} only sees bean definitions already contributed, so both the EMF and the {@link
 * CryptoService} must be registered before this condition is evaluated. Omitting the crypto
 * ordering makes the match depend on incidental auto-configuration order — it happened to work in
 * {@code common}'s own test yet silently skipped the service in the admin host. Registered from
 * {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, CryptoAutoConfiguration.class})
@AutoConfigurationPackage(basePackages = "io.github.degdev.engine.common")
public class SettingsAutoConfiguration {

  /**
   * Provides the settings service when JPA and crypto are both active.
   *
   * @param repository the settings repository (scanned via this config's auto-configuration
   *     package)
   * @param cryptoService the secret encrypt/decrypt service
   * @return the settings service
   */
  @Bean
  @ConditionalOnBean({EntityManagerFactory.class, CryptoService.class})
  public SettingsService settingsService(
      SettingRepository repository, CryptoService cryptoService) {
    return new SettingsService(repository, cryptoService);
  }
}
