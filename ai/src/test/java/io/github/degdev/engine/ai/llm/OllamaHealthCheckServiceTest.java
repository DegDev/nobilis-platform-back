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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * No real HTTP — {@link MockRestServiceServer} stubs Ollama's native {@code /api/tags}. Proves
 * {@link OllamaHealthCheckService#check(String)} never throws (unreachable/error become {@code
 * ok=false}) and the {@code :latest} tolerance rule.
 */
class OllamaHealthCheckServiceTest {

  private static final String BASE_URL = "http://localhost:11434";

  private MockRestServiceServer server;
  private OllamaHealthCheckService healthCheck;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    healthCheck = new OllamaHealthCheckService(builder.build());
  }

  @Test
  void modelPresentIsOk() {
    server
        .expect(requestTo(BASE_URL + "/api/tags"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"models\": [{\"name\": \"qwen3:8b\"}, {\"name\": \"llama3:latest\"}]}",
                MediaType.APPLICATION_JSON));

    AiHealthCheckResult result = healthCheck.check("qwen3:8b");

    assertThat(result.ok()).isTrue();
    assertThat(result.message()).contains("qwen3:8b");
    server.verify();
  }

  @Test
  void bareModelNameToleratesAnInstalledLatestTag() {
    server
        .expect(requestTo(BASE_URL + "/api/tags"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"models\": [{\"name\": \"llama3:latest\"}]}", MediaType.APPLICATION_JSON));

    AiHealthCheckResult result = healthCheck.check("llama3");

    assertThat(result.ok()).isTrue();
  }

  @Test
  void modelAbsentIsNotOk() {
    server
        .expect(requestTo(BASE_URL + "/api/tags"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"models\": [{\"name\": \"llama3:latest\"}]}", MediaType.APPLICATION_JSON));

    AiHealthCheckResult result = healthCheck.check("qwen3:8b");

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("qwen3:8b").contains("not installed");
  }

  @Test
  void serverErrorIsNotOkAndDoesNotThrow() {
    server
        .expect(requestTo(BASE_URL + "/api/tags"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    AiHealthCheckResult result = healthCheck.check("llama3");

    assertThat(result.ok()).isFalse();
  }

  @Test
  void unreachableHostIsNotOkAndDoesNotThrow() {
    RestClient unreachable = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
    OllamaHealthCheckService unreachableCheck = new OllamaHealthCheckService(unreachable);

    AiHealthCheckResult result = unreachableCheck.check("llama3");

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("unreachable");
  }
}
