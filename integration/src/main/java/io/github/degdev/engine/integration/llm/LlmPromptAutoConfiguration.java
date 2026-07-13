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

import io.github.degdev.engine.ai.llm.AiLlmAutoConfiguration;
import io.github.degdev.engine.ai.llm.LlmClient;
import io.github.degdev.engine.ai.persistence.AiServiceAutoConfiguration;
import io.github.degdev.engine.ai.profile.AiProfileService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contributes {@link LlmPromptEventHandler} only when BOTH {@link AiProfileService} (JPA-backed
 * profile resolve) and {@link LlmClient} (a configured provider base URL) are present — a worker
 * missing either simply doesn't mount this handler and still boots, mirroring {@code
 * AiAdminAutoConfiguration}'s single-collaborator gate extended to two independent collaborators.
 * {@code @ConditionalOnBean} is only reliable against beans from OTHER autoconfiguration classes
 * when this class is itself an autoconfiguration (deferred-import ordering) — a plain
 * {@code @Component} with the same condition would evaluate before {@link
 * AiServiceAutoConfiguration}/ {@link AiLlmAutoConfiguration} run and always see both beans as
 * absent (the same standing trap documented on {@code AiServiceAutoConfiguration}, here applied to
 * a component-scanned worker module rather than an admin host). Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {AiServiceAutoConfiguration.class, AiLlmAutoConfiguration.class})
@ConditionalOnBean({AiProfileService.class, LlmClient.class})
public class LlmPromptAutoConfiguration {

  /**
   * Provides the handler when both an AI profile store and an LLM client are active.
   *
   * @param profileService resolves the provider/params for an event's purpose
   * @param llmClient completes the resolved request
   * @param jsonMapper deserializes the event payload
   * @return the handler
   */
  @Bean
  public LlmPromptEventHandler llmPromptEventHandler(
      AiProfileService profileService, LlmClient llmClient, JsonMapper jsonMapper) {
    return new LlmPromptEventHandler(profileService, llmClient, jsonMapper);
  }
}
