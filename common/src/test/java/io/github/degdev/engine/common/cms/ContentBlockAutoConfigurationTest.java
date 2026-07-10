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
package io.github.degdev.engine.common.cms;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.degdev.engine.common.i18n.I18nAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the stateless-host contract without a real database: with no {@code EntityManagerFactory}
 * bean on the context, {@link ContentBlockAutoConfiguration} contributes no {@link
 * ContentBlockService} — the mounting condition is the EMF bean, not the classpath. The presence
 * case (a real JPA host DOES get the service) is proven by {@link ContentBlockCrudIntegrationTest},
 * which autowires {@link ContentBlockService} against a live Testcontainers datasource.
 */
class ContentBlockAutoConfigurationTest {

  @Test
  void serviceIsAbsentWithoutAnEntityManagerFactory() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(I18nAutoConfiguration.class, ContentBlockAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ContentBlockService.class));
  }
}
