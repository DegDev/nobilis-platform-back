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

import io.github.degdev.engine.app.health.HealthController;
import io.github.degdev.engine.common.crypto.CryptoService;
import io.github.degdev.engine.common.i18n.LocaleResolver;
import io.github.degdev.engine.common.persistence.SystemAuditorAware;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Proves the portal host boots as a STATELESS web application — with {@code common}'s JPA/Flyway on
 * the classpath but no database configured — because {@code application.properties} excludes the
 * DataSource/JPA/Flyway auto-configurations. Before those exclusions the host did not start at all:
 * {@code DataSourceAutoConfiguration} failed with "Failed to configure a DataSource". This test is
 * what keeps that regression from returning.
 *
 * <p>Also pins which of {@code common}'s auto-configurations reach a portal host: i18n mounts
 * unconditionally, while crypto (gated on {@code nobilis.crypto.master-key}) and JPA auditing
 * (gated on an {@code EntityManagerFactory}) correctly stay absent — the portal supplies neither.
 */
@SpringBootTest
class AppApplicationTest {

  @Autowired private ApplicationContext context;

  @Test
  void bootsWithoutADatabase() {
    assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
  }

  @Test
  void mountsTheHealthEndpoint() {
    assertThat(context.getBeanNamesForType(HealthController.class)).hasSize(1);
  }

  @Test
  void mountsCommonI18nButNotCryptoOrAuditing() {
    assertThat(context.getBeanNamesForType(LocaleResolver.class)).hasSize(1);
    // Crypto needs a master key, auditing needs a JPA EntityManagerFactory; the portal has neither.
    assertThat(context.getBeanNamesForType(CryptoService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(SystemAuditorAware.class)).isEmpty();
  }
}
