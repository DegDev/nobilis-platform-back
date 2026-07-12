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
package io.github.degdev.engine.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves {@link AiLlmAutoConfiguration}'s opt-in gate without a real Ollama instance: absent {@code
 * nobilis.ai.base-url}, neither {@link LlmClient} nor {@link OllamaHealthCheckService} mounts
 * (never a fail-fast boot); with it set, both mount as beans. Mirrors {@code
 * ContentBlockAutoConfigurationTest}'s {@link ApplicationContextRunner} shape.
 */
class AiLlmAutoConfigurationTest {

  @Test
  void neitherBeanMountsWithoutABaseUrl() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiLlmAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LlmClient.class);
              assertThat(context).doesNotHaveBean(OllamaHealthCheckService.class);
            });
  }

  @Test
  void bothBeansMountWithABaseUrlConfigured() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiLlmAutoConfiguration.class))
        .withPropertyValues("nobilis.ai.base-url=http://localhost:11434")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LlmClient.class);
              assertThat(context).hasSingleBean(OllamaHealthCheckService.class);
            });
  }
}
