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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * Boots the admin HTTP application.
 *
 * <p>This milestone-03 vertical slice is deliberately STATELESS. The {@code common} library drags
 * Spring Data JPA, Hibernate and Flyway onto the classpath, but the slice (admin login + the
 * contour policy + a placeholder screen) touches no database. Their auto-configurations are
 * excluded so the host boots without a running Postgres; persistence returns in a later pass. The
 * exclusions use the Spring Boot 4 module-split package names (e.g. {@code
 * org.springframework.boot.jdbc.autoconfigure}), not the pre-4 {@code
 * org.springframework.boot.autoconfigure.*} ones.
 */
@SpringBootApplication(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      DataJpaRepositoriesAutoConfiguration.class,
      FlywayAutoConfiguration.class
    })
public class AdminApplication {

  public static void main(String[] args) {
    SpringApplication.run(AdminApplication.class, args);
  }
}
