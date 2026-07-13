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

import io.github.degdev.engine.ai.llm.LlmClient;
import io.github.degdev.engine.ai.llm.LlmException;
import io.github.degdev.engine.ai.llm.LlmPromptEvent;
import io.github.degdev.engine.ai.llm.LlmRequest;
import io.github.degdev.engine.ai.llm.LlmResponse;
import io.github.degdev.engine.ai.llm.Message;
import io.github.degdev.engine.ai.profile.AiProfileException;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.ResolvedAiProfile;
import io.github.degdev.engine.common.bus.EventHandler;
import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Slice-6 proof-of-pipe consumer: consumes an {@link LlmPromptEvent} off {@link #TOPIC}, resolves
 * the profile for its purpose via {@link AiProfileService#resolve(String)}, completes it through
 * {@link LlmClient}, and logs the response. No persistence, no classifier, no follow-up event — a
 * domain consumer (a future milestone) rides this same mechanism with its own richer event, rather
 * than this handler growing domain fields.
 *
 * <p>Failure classification (deferred from {@link LlmException}'s slice-3 javadoc to here, where a
 * bus context finally exists): {@code OllamaLlmClient}'s cause chain already carries the HTTP
 * exception type, so it is inspected directly — {@code HttpClientErrorException} (4xx: bad
 * request/unknown model) is {@link TerminalBusException}; {@code HttpServerErrorException}/{@code
 * ResourceAccessException} (5xx/timeout/network) is {@link RetriableBusException}. A cause-less
 * {@link LlmException} (an empty/malformed response, Ollama's 200-OK-with-error-body case) falls
 * back to retriable — the same catch-all stance {@link
 * io.github.degdev.engine.integration.dispatch.NotificationDispatchEventHandler} takes for an
 * unclassified transport failure.
 */
public class LlmPromptEventHandler implements EventHandler {

  static final String TOPIC = "nobilis.ai.llm-prompt";

  private static final Logger LOG = LoggerFactory.getLogger(LlmPromptEventHandler.class);

  private final AiProfileService profileService;
  private final LlmClient llmClient;
  private final JsonMapper jsonMapper;

  public LlmPromptEventHandler(
      AiProfileService profileService, LlmClient llmClient, JsonMapper jsonMapper) {
    this.profileService = profileService;
    this.llmClient = llmClient;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void handle(String payload) {
    LlmPromptEvent event;
    try {
      event = jsonMapper.readValue(payload, LlmPromptEvent.class);
    } catch (Exception e) {
      throw new TerminalBusException("Unparseable LLM prompt event: " + payload, e);
    }

    ResolvedAiProfile resolved;
    try {
      resolved = profileService.resolve(event.purpose());
    } catch (AiProfileException e) {
      throw new TerminalBusException(
          "No AI profile resolvable for purpose '" + event.purpose() + "'", e);
    }

    LlmRequest request = toRequest(resolved.params(), event.prompt());
    LlmResponse response;
    try {
      response = llmClient.complete(request);
    } catch (LlmException e) {
      throw classify(e);
    }

    LOG.info("LLM response for purpose '{}': {}", event.purpose(), response.content());
  }

  private static LlmRequest toRequest(Map<String, String> params, String prompt) {
    return new LlmRequest(
        params.get("model"),
        List.of(new Message("user", prompt)),
        parseDouble(params.get("temperature")),
        parseDouble(params.get("top_p")),
        parseInt(params.get("num_predict")),
        parseBoolean(params.get("no-think")));
  }

  private static RuntimeException classify(LlmException e) {
    Throwable cause = e.getCause();
    if (cause instanceof HttpClientErrorException) {
      return new TerminalBusException(e.getMessage(), e);
    }
    return new RetriableBusException(e.getMessage(), e);
  }

  private static Double parseDouble(String value) {
    return value == null ? null : Double.valueOf(value);
  }

  private static Integer parseInt(String value) {
    return value == null ? null : Integer.valueOf(value);
  }

  private static Boolean parseBoolean(String value) {
    return value == null ? null : Boolean.valueOf(value);
  }
}
