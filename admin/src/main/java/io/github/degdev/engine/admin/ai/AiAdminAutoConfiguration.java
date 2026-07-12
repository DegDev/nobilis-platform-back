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
package io.github.degdev.engine.admin.ai;

import io.github.degdev.engine.ai.llm.OllamaHealthCheckService;
import io.github.degdev.engine.ai.persistence.AiServiceAutoConfiguration;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.AiSecretStore;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the AI-profile screen only when its profile store exists — the same shape as {@code
 * SettingsWebAutoConfiguration}/{@code RoleAdminAutoConfiguration}. {@link AiProfileController} is
 * a request handler but deliberately NOT a component-scanned {@code @Controller}; this
 * auto-configuration registers it as a {@code @Bean} gated on {@link AiProfileService} being
 * present, which happens exactly when the database is active (see {@link
 * AiServiceAutoConfiguration}).
 *
 * <p>{@link AiSecretStore} and {@link OllamaHealthCheckService} are NOT part of this gate — they
 * mount independently (crypto-gated and base-url-gated respectively) and are injected into the
 * controller as {@link ObjectProvider}s, resolved lazily per request, so their presence/absence
 * never affects whether this configuration itself applies. Registered from admin's {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = AiServiceAutoConfiguration.class)
@ConditionalOnBean(AiProfileService.class)
public class AiAdminAutoConfiguration {

  /**
   * Registers the AI-profile controller when the store is present.
   *
   * @param providerDefaults read-only catalog access
   * @param profileService profile resolve/save
   * @param secretStoreProvider the secret store, if crypto is configured
   * @param healthCheckProvider the health-check probe, if an LLM base URL is configured
   * @return the AI-profile controller
   */
  @Bean
  public AiProfileController aiProfileController(
      AiProviderDefaults providerDefaults,
      AiProfileService profileService,
      ObjectProvider<AiSecretStore> secretStoreProvider,
      ObjectProvider<OllamaHealthCheckService> healthCheckProvider) {
    return new AiProfileController(
        providerDefaults, profileService, secretStoreProvider, healthCheckProvider);
  }
}
