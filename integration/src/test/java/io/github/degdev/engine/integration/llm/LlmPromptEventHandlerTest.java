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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.ai.llm.LlmClient;
import io.github.degdev.engine.ai.llm.LlmException;
import io.github.degdev.engine.ai.llm.LlmRequest;
import io.github.degdev.engine.ai.llm.LlmResponse;
import io.github.degdev.engine.ai.profile.AiProfileException;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.ResolvedAiProfile;
import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mirrors {@code NotificationDispatchEventHandlerTest}'s structure: {@link LlmPromptEventHandler}
 * must throw typed exceptions instead of swallowing failures, so the bus-level retry/DLQ machinery
 * can act on them, and must resolve the purpose's profile params onto the {@link LlmClient} request
 * it builds.
 */
@ExtendWith(MockitoExtension.class)
class LlmPromptEventHandlerTest {

  private static final String VALID_PAYLOAD =
      """
      {"purpose":"default","prompt":"Say hi"}\
      """;

  @Mock private AiProfileService profileService;
  @Mock private LlmClient llmClient;

  private LlmPromptEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new LlmPromptEventHandler(profileService, llmClient, new JsonMapper());
  }

  @Test
  void unparseablePayloadIsTerminal() {
    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> handler.handle("not json"));

    verifyNoInteractions(profileService, llmClient);
  }

  @Test
  void noResolvableProfileIsTerminal() {
    when(profileService.resolve("default"))
        .thenThrow(new AiProfileException("error.ai-no-default-provider-for-purpose", "default"));

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> handler.handle(VALID_PAYLOAD));

    verifyNoInteractions(llmClient);
  }

  @Test
  void resolvedProfileParamsFlowIntoTheLlmRequest() {
    when(profileService.resolve("default"))
        .thenReturn(
            new ResolvedAiProfile(
                "default",
                "ollama",
                Map.of(
                    "model", "qwen3:8b",
                    "temperature", "0.2",
                    "top_p", "0.9",
                    "num_predict", "256",
                    "no-think", "true")));
    when(llmClient.complete(any())).thenReturn(new LlmResponse("hi there", 3, 2, "stop", null));

    handler.handle(VALID_PAYLOAD);

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmClient).complete(captor.capture());
    LlmRequest request = captor.getValue();
    assertThat(request.model()).isEqualTo("qwen3:8b");
    assertThat(request.temperature()).isEqualTo(0.2);
    assertThat(request.topP()).isEqualTo(0.9);
    assertThat(request.numPredict()).isEqualTo(256);
    assertThat(request.noThink()).isTrue();
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().get(0).role()).isEqualTo("user");
    assertThat(request.messages().get(0).content()).isEqualTo("Say hi");

    assertThat(handler.topic()).isEqualTo(LlmPromptEventHandler.TOPIC);
  }

  @Test
  void a4xxLlmFailureIsTerminal() {
    when(profileService.resolve("default"))
        .thenReturn(new ResolvedAiProfile("default", "ollama", Map.of("model", "llama3")));
    HttpClientErrorException cause =
        HttpClientErrorException.create(
            HttpStatusCode.valueOf(400), "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
    when(llmClient.complete(any()))
        .thenThrow(new LlmException("Ollama rejected the chat request", cause));

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> handler.handle(VALID_PAYLOAD));
  }

  @Test
  void a5xxLlmFailureIsRetriable() {
    when(profileService.resolve("default"))
        .thenReturn(new ResolvedAiProfile("default", "ollama", Map.of("model", "llama3")));
    HttpServerErrorException cause =
        HttpServerErrorException.create(
            HttpStatusCode.valueOf(503),
            "Service Unavailable",
            HttpHeaders.EMPTY,
            new byte[0],
            null);
    when(llmClient.complete(any()))
        .thenThrow(new LlmException("Ollama chat request failed", cause));

    assertThatExceptionOfType(RetriableBusException.class)
        .isThrownBy(() -> handler.handle(VALID_PAYLOAD));
  }

  @Test
  void aCauselessLlmFailureFallsBackToRetriable() {
    when(profileService.resolve("default"))
        .thenReturn(new ResolvedAiProfile("default", "ollama", Map.of("model", "llama3")));
    when(llmClient.complete(any()))
        .thenThrow(new LlmException("Ollama chat response carried no message"));

    assertThatExceptionOfType(RetriableBusException.class)
        .isThrownBy(() -> handler.handle(VALID_PAYLOAD));
  }
}
