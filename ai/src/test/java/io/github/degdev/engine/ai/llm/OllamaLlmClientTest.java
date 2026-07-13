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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * No real HTTP — {@link MockRestServiceServer} stubs Ollama's native {@code /api/chat}. Mirrors
 * {@code TelegramNotificationTransportTest}'s shape. Asserts the native wire body (an {@code
 * options} bag with {@code num_predict}, never an OpenAI-compat {@code max_tokens}) and the
 * 200-OK-with-error-body gotcha.
 */
class OllamaLlmClientTest {

  private static final String BASE_URL = "http://localhost:11434";

  private MockRestServiceServer server;
  private OllamaLlmClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OllamaLlmClient(builder.build());
  }

  @Test
  void successfulCompleteParsesMessageContentAndSendsTheNativeWireBody() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "model": "llama3",
                      "messages": [{"role": "user", "content": "hello"}],
                      "stream": false,
                      "options": {"temperature": 0.7, "top_p": 0.9, "num_predict": 512},
                      "think": false
                    }
                    """,
                    true))
        .andRespond(
            withSuccess(
                """
                {
                  "message": {"role": "assistant", "content": "hi there"},
                  "done": true,
                  "done_reason": "stop",
                  "prompt_eval_count": 5,
                  "eval_count": 3
                }
                """,
                MediaType.APPLICATION_JSON));

    LlmResponse response =
        client.complete(
            new LlmRequest("llama3", List.of(new Message("user", "hello")), 0.7, 0.9, 512, true));

    assertThat(response.content()).isEqualTo("hi there");
    assertThat(response.doneReason()).isEqualTo("stop");
    assertThat(response.promptEvalCount()).isEqualTo(5);
    assertThat(response.evalCount()).isEqualTo(3);
    assertThat(response.thinking()).isNull();
    server.verify();
  }

  @Test
  void noThinkFalseOrAbsentOmitsTheThinkFieldEntirely() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "model": "llama3",
                      "messages": [{"role": "user", "content": "hello"}],
                      "stream": false,
                      "options": {"temperature": null, "top_p": null, "num_predict": null}
                    }
                    """,
                    true))
        .andRespond(
            withSuccess(
                """
                {"message": {"role": "assistant", "content": "hi there"}}
                """,
                MediaType.APPLICATION_JSON));

    client.complete(
        new LlmRequest("llama3", List.of(new Message("user", "hello")), null, null, null, null));

    server.verify();
  }

  @Test
  void unsuppressedThinkModelReasoningIsCapturedNotDropped() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {
                  "message": {
                    "role": "assistant",
                    "content": "hi there",
                    "thinking": "the user said hello, I should greet back"
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    LlmResponse response =
        client.complete(
            new LlmRequest(
                "qwen3:8b", List.of(new Message("user", "hello")), null, null, null, false));

    assertThat(response.content()).isEqualTo("hi there");
    assertThat(response.thinking()).isEqualTo("the user said hello, I should greet back");
    server.verify();
  }

  @Test
  void twoHundredOkWithErrorBodyThrowsLlmException() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess("{\"error\": \"model 'bogus' not found\"}", MediaType.APPLICATION_JSON));

    assertThatExceptionOfType(LlmException.class)
        .isThrownBy(
            () ->
                client.complete(
                    new LlmRequest(
                        "bogus", List.of(new Message("user", "hello")), null, null, null, null)))
        .withMessageContaining("model 'bogus' not found");
    server.verify();
  }

  @Test
  void clientErrorThrowsLlmException() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"bad request\"}"));

    assertThatExceptionOfType(LlmException.class)
        .isThrownBy(
            () ->
                client.complete(
                    new LlmRequest(
                        "llama3", List.of(new Message("user", "hello")), null, null, null, null)));
    server.verify();
  }

  @Test
  void serverErrorThrowsLlmException() {
    server
        .expect(requestTo(BASE_URL + "/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatExceptionOfType(LlmException.class)
        .isThrownBy(
            () ->
                client.complete(
                    new LlmRequest(
                        "llama3", List.of(new Message("user", "hello")), null, null, null, null)));
    server.verify();
  }
}
