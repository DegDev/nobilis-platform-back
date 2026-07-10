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
package io.github.degdev.engine.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Reproduces, and guards against the regression of, the NB-MIGRATIONS defect: {@code app} started
 * with a datasource profile and Flyway failed validation with "Detected applied migration not
 * resolved locally: 2, 3, 4" — {@code admin} (depends on {@code common} + {@code auth}) had already
 * applied {@code auth}'s migrations to the shared Postgres, but {@code app} (depends on {@code
 * common} only) did not carry those files on its own classpath, since they lived under {@code
 * auth/src/main/resources/db/migration}.
 *
 * <p>Now that every migration lives in {@code common/src/main/resources/db/migration}, {@code
 * app}'s classpath resolves the identical full set. This test migrates a persistent Postgres
 * directly via the Flyway API — standing in for a wider-classpath runnable (e.g. {@code admin})
 * having already applied the full history, exactly as in the original incident — then boots the
 * real {@link AppApplication} with a datasource profile against that SAME, already-migrated
 * database and asserts it starts cleanly: Flyway validates green, applies nothing new, and does not
 * throw.
 */
@Testcontainers
class MultiRunnableSharedDatabaseIntegrationTest {

  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @BeforeAll
  static void startDatabase() {
    POSTGRES.start();
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Test
  void appBootsWithADatasourceAfterTheSharedDatabaseWasAlreadyMigrated() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();

    try (ConfigurableApplicationContext appContext =
        new SpringApplicationBuilder(AppApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.autoconfigure.exclude=",
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false")
            .run()) {
      assertThat(appContext.isActive()).isTrue();
    }
  }
}
