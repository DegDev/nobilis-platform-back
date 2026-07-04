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
package io.github.degdev.engine.admin;

import io.github.degdev.engine.admin.accounts.AccountController;
import io.github.degdev.engine.admin.roles.RoleController;
import io.github.degdev.engine.admin.settings.SettingsController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

/**
 * Boots the admin HTTP application.
 *
 * <p>The host is STATELESS by default: {@code common} drags Spring Data JPA, Hibernate and Flyway
 * onto the classpath, but the default configuration touches no database, so those
 * auto-configurations are excluded and the host boots without a running Postgres. The exclusions
 * live in {@code application.properties} as {@code spring.autoconfigure.exclude} precisely so a
 * profile can turn persistence back on with a one-line override: {@code
 * application-local.properties} resets the list to empty and supplies a datasource. Config flips
 * the database on; no code change is needed. The excluded FQNs use the Spring Boot 4 module-split
 * package names (e.g. {@code org.springframework.boot.jdbc.autoconfigure}), not the pre-4 {@code
 * org.springframework.boot.autoconfigure.*} ones.
 *
 * <p>This is {@code @SpringBootApplication} spelled out ({@code @SpringBootConfiguration} +
 * {@code @EnableAutoConfiguration} + {@code @ComponentScan} with Boot's two standard exclude
 * filters) for one reason: the extra exclude filter keeps the DB-only screens ({@link
 * SettingsController}, {@link RoleController}, {@link AccountController}) OUT of the component
 * scan. Each is registered as a {@code @Bean} by its web auto-configuration only when its store
 * exists — so scanning them here would both mount them in the stateless host (where they have no
 * service to inject, failing the boot) and clash with the conditional bean. Everything else scans
 * as usual.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters = {
      @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
      @Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          classes = {SettingsController.class, RoleController.class, AccountController.class})
    })
public class AdminApplication {

  public static void main(String[] args) {
    SpringApplication.run(AdminApplication.class, args);
  }
}
