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
package io.github.degdev.engine.admin.settings;

import io.github.degdev.engine.common.settings.SettingsAutoConfiguration;
import io.github.degdev.engine.common.settings.SettingsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the settings screen only when its store exists. The {@link SettingsController} is a
 * request handler but deliberately NOT a component-scanned {@code @Controller}; this
 * auto-configuration registers it as a {@code @Bean} gated on a {@link SettingsService} being
 * present — which happens exactly when the database is active (see {@link
 * SettingsAutoConfiguration}). So the stateless default admin host mounts no settings endpoints,
 * while the DB-enabled host gets the full screen.
 *
 * <p>{@link ConditionalOnBean} is reliable here precisely because this is an auto-configuration,
 * evaluated after regular configuration and after {@code common}'s {@link
 * SettingsAutoConfiguration} (declared via {@code after}) has had its chance to contribute the
 * service — the ordering guarantee a component-scanned {@code @ConditionalOnBean} would not have.
 * Registered from admin's {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = SettingsAutoConfiguration.class)
@ConditionalOnBean(SettingsService.class)
public class SettingsWebAutoConfiguration {

  /**
   * Registers the settings controller when the store is present.
   *
   * @param settingsService the settings store
   * @return the settings CRUD controller
   */
  @Bean
  public SettingsController settingsController(SettingsService settingsService) {
    return new SettingsController(settingsService);
  }
}
