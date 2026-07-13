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
package io.github.degdev.engine.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.degdev.engine.ai.llm.LlmClient;
import io.github.degdev.engine.ai.profile.AiProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves {@link LlmPromptAutoConfiguration}'s double gate directly, without a real database or
 * Ollama endpoint: {@link AiProfileService}/{@link LlmClient} are pushed in as plain mock beans
 * (the upstream autoconfigurations that would normally supply them — {@code
 * AiServiceAutoConfiguration}'s EMF gate, {@code AiLlmAutoConfiguration}'s base-url gate — are
 * already proven independently by their own tests). Mirrors {@code AiLlmAutoConfigurationTest}'s
 * {@link ApplicationContextRunner} shape.
 */
class LlmPromptAutoConfigurationTest {

  @Test
  void handlerDoesNotMountWithNeitherCollaborator() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LlmPromptAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(LlmPromptEventHandler.class));
  }

  @Test
  void handlerDoesNotMountWithOnlyTheLlmClient() {
    new ApplicationContextRunner()
        .withBean(LlmClient.class, () -> mock(LlmClient.class))
        .withConfiguration(AutoConfigurations.of(LlmPromptAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(LlmPromptEventHandler.class));
  }

  @Test
  void handlerDoesNotMountWithOnlyTheProfileService() {
    new ApplicationContextRunner()
        .withBean(AiProfileService.class, () -> mock(AiProfileService.class))
        .withConfiguration(AutoConfigurations.of(LlmPromptAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(LlmPromptEventHandler.class));
  }

  @Test
  void handlerMountsWithBothCollaborators() {
    new ApplicationContextRunner()
        .withBean(AiProfileService.class, () -> mock(AiProfileService.class))
        .withBean(LlmClient.class, () -> mock(LlmClient.class))
        .withBean(JsonMapper.class, JsonMapper::shared)
        .withConfiguration(AutoConfigurations.of(LlmPromptAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(LlmPromptEventHandler.class));
  }
}
