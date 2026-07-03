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
package io.github.degdev.engine.admin.roles;

import io.github.degdev.engine.auth.role.RoleService;
import io.github.degdev.engine.auth.role.RoleServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the roles screen only when its store exists — the same shape as {@code
 * SettingsWebAutoConfiguration}. {@link RoleController} is a request handler but deliberately NOT a
 * component-scanned {@code @Controller}; this auto-configuration registers it as a {@code @Bean}
 * gated on a {@link RoleService} being present, which happens exactly when the database is active
 * (see {@link RoleServiceAutoConfiguration}). The stateless default host mounts no roles endpoints.
 *
 * <p>{@link ConditionalOnBean} is reliable here because this is an auto-configuration, evaluated
 * after regular configuration and (via {@code after}) after auth's {@link
 * RoleServiceAutoConfiguration} has had its chance to contribute the service. Registered from
 * admin's {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = RoleServiceAutoConfiguration.class)
@ConditionalOnBean(RoleService.class)
public class RoleAdminAutoConfiguration {

  /**
   * Registers the roles controller when the store is present.
   *
   * @param roleService the role management service
   * @return the roles CRUD controller
   */
  @Bean
  public RoleController roleController(RoleService roleService) {
    return new RoleController(roleService);
  }
}
