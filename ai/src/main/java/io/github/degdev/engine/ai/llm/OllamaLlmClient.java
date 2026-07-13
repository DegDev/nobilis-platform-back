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

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link LlmClient} adapter for Ollama, hand-rolled {@link RestClient} — mirrors {@code
 * TelegramNotificationTransport}'s house style (no framework LLM client, e.g. Spring AI; {@code
 * ai/pom.xml} states the module stays thin/opt-in). Posts to Ollama's NATIVE {@code /api/chat}
 * (Fork 5, LOCKED), not an OpenAI-compat endpoint — {@link OllamaChatRequest}/{@link OllamaOptions}
 * carry Ollama's own field names ({@code top_p}, {@code num_predict}).
 *
 * <p>Wired as an explicit {@code @Bean} by {@link AiLlmAutoConfiguration}, not a component-scanned
 * {@code @Service} — {@code io.github.degdev.engine.ai} is never reached by a real host's scan
 * root, same reasoning as every other {@code ai} service.
 */
class OllamaLlmClient implements LlmClient {

  private final RestClient restClient;

  OllamaLlmClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    // think is TOP-LEVEL (a sibling of options), not nested inside it — confirmed live. Only an
    // explicit noThink=true suppresses reasoning (think:false); false/null omits the field
    // entirely (via OllamaChatRequest's @JsonInclude(NON_NULL)) rather than sending think:true, so
    // a profile that never touches this field sends the same body shape as before it existed.
    Boolean think = Boolean.TRUE.equals(request.noThink()) ? Boolean.FALSE : null;
    OllamaChatRequest wireRequest =
        new OllamaChatRequest(
            request.model(),
            request.messages(),
            false,
            new OllamaOptions(request.temperature(), request.topP(), request.numPredict()),
            think);

    OllamaChatResponse wireResponse;
    try {
      wireResponse =
          restClient
              .post()
              .uri("/api/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .body(wireRequest)
              .retrieve()
              .body(OllamaChatResponse.class);
    } catch (HttpClientErrorException e) {
      throw new LlmException("Ollama rejected the chat request: " + e.getMessage(), e);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new LlmException("Ollama chat request failed: " + e.getMessage(), e);
    }

    if (wireResponse == null) {
      throw new LlmException("Ollama returned an empty chat response");
    }
    // The 200-OK-with-error-body case: Ollama can answer 200 with {"error": "..."} instead of a
    // message — a naive "HTTP 200 = success" check would silently return the error text as if it
    // were the reply, so this is checked explicitly before trusting the body.
    if (wireResponse.error() != null) {
      throw new LlmException("Ollama chat request failed: " + wireResponse.error());
    }
    if (wireResponse.message() == null) {
      throw new LlmException("Ollama chat response carried no message");
    }

    return new LlmResponse(
        wireResponse.message().content(),
        wireResponse.promptEvalCount(),
        wireResponse.evalCount(),
        wireResponse.doneReason(),
        wireResponse.message().thinking());
  }
}
