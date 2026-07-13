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
package io.github.degdev.engine.ai.persistence;

import io.github.degdev.engine.ai.profile.AiProfileParamRepository;
import io.github.degdev.engine.ai.profile.AiProfileRepository;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.AiSecretRepository;
import io.github.degdev.engine.ai.profile.AiSecretStore;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import io.github.degdev.engine.ai.provider.AiProviderFieldOptionRepository;
import io.github.degdev.engine.ai.provider.AiProviderFieldRepository;
import io.github.degdev.engine.ai.provider.AiProviderPurposeRepository;
import io.github.degdev.engine.ai.provider.AiProviderRepository;
import io.github.degdev.engine.common.crypto.CryptoAutoConfiguration;
import io.github.degdev.engine.common.crypto.CryptoService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Contributes {@link AiProviderDefaults}/{@link AiProfileService}/{@link AiSecretStore} when their
 * collaborators are active, mirroring how {@code common}'s {@code SettingsAutoConfiguration} and
 * {@code auth}'s {@code RoleServiceAutoConfiguration} gate their services.
 *
 * <p>{@link AiProviderDefaults} and {@link AiProfileService} are gated on {@link
 * ConditionalOnBean}({@code EntityManagerFactory}) only — the repositories they need come from
 * {@code AiPersistenceAutoConfiguration}'s package registration, which is unconditional, so once an
 * EMF exists the repositories certainly do too (same reasoning as {@code
 * RoleServiceAutoConfiguration}: gate on the EMF bean, not on a repository directly, since
 * repository bean registration order relative to this class is not guaranteed).
 *
 * <p>{@link AiSecretStore} additionally requires {@link CryptoService} — a two-collaborator {@link
 * ConditionalOnBean}, mirroring {@code SettingsAutoConfiguration}'s {@code {EntityManagerFactory,
 * CryptoService}} gate — so a host with JPA but no configured master key gets the rest of the AI
 * service layer but no secret store, never a fail-fast boot. Ordered {@code after} both {@link
 * HibernateJpaAutoConfiguration} and {@link CryptoAutoConfiguration} so both beans are already
 * registered when the conditions are evaluated. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, CryptoAutoConfiguration.class})
@ConditionalOnBean(EntityManagerFactory.class)
public class AiServiceAutoConfiguration {

  /**
   * Provides read-only catalog access when JPA is active.
   *
   * @param fieldRepository the catalog field repository
   * @param optionRepository the catalog field-option repository
   * @param purposeRepository the purpose/provider link repository
   * @param providerRepository the provider catalog repository
   * @return the provider-defaults reader
   */
  @Bean
  public AiProviderDefaults aiProviderDefaults(
      AiProviderFieldRepository fieldRepository,
      AiProviderFieldOptionRepository optionRepository,
      AiProviderPurposeRepository purposeRepository,
      AiProviderRepository providerRepository) {
    return new AiProviderDefaults(
        fieldRepository, optionRepository, purposeRepository, providerRepository);
  }

  /**
   * Provides profile resolve/save when JPA is active.
   *
   * @param profileRepository the profile repository
   * @param paramRepository the profile-param repository
   * @param providerRepository the provider catalog repository
   * @param providerDefaults the catalog reader this service resolves defaults from
   * @return the profile service
   */
  @Bean
  public AiProfileService aiProfileService(
      AiProfileRepository profileRepository,
      AiProfileParamRepository paramRepository,
      AiProviderRepository providerRepository,
      AiProviderDefaults providerDefaults) {
    return new AiProfileService(
        profileRepository, paramRepository, providerRepository, providerDefaults);
  }

  /**
   * Provides the secret store when JPA AND crypto are both active.
   *
   * @param repository the secret repository
   * @param cryptoService the secret encrypt/decrypt service
   * @return the secret store
   */
  @Bean
  @ConditionalOnBean(CryptoService.class)
  public AiSecretStore aiSecretStore(AiSecretRepository repository, CryptoService cryptoService) {
    return new AiSecretStore(repository, cryptoService);
  }
}
