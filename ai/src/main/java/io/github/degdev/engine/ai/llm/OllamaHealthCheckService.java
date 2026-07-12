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

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Probes an Ollama instance's reachability and whether a given model is installed, via Ollama's
 * NATIVE {@code GET /api/tags} (Fork 5, LOCKED) — never throws, so a future on-demand "check" UI
 * action can show "Ollama unreachable" instead of failing with a 500.
 *
 * <p>Wired as an explicit {@code @Bean} by {@link AiLlmAutoConfiguration}, not a component-scanned
 * {@code @Service} — same reasoning as {@link OllamaLlmClient}.
 */
class OllamaHealthCheckService {

  private static final String LATEST_SUFFIX = ":latest";

  private final RestClient restClient;

  OllamaHealthCheckService(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Checks that Ollama is reachable and the given model is installed.
   *
   * @param model the configured model name (e.g. {@code "llama3"} or {@code "qwen3:8b"})
   * @return {@code ok=true} with a confirmation message if the model is present; {@code ok=false}
   *     with a human explanation otherwise (unreachable, timeout, or model not installed) — never
   *     throws
   */
  AiHealthCheckResult check(String model) {
    OllamaTagsResponse response;
    try {
      response = restClient.get().uri("/api/tags").retrieve().body(OllamaTagsResponse.class);
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      return new AiHealthCheckResult(false, "Ollama returned an error: " + e.getMessage());
    } catch (ResourceAccessException e) {
      return new AiHealthCheckResult(false, "Ollama is unreachable: " + e.getMessage());
    }

    if (response == null || response.models() == null) {
      return new AiHealthCheckResult(false, "Ollama returned no models");
    }

    boolean present = response.models().stream().anyMatch(m -> matches(model, m.name()));
    return present
        ? new AiHealthCheckResult(true, "Model '" + model + "' is available")
        : new AiHealthCheckResult(
            false, "Model '" + model + "' is not installed on this Ollama instance");
  }

  /**
   * Whether a catalog-configured model name matches an installed tag, tolerating Ollama's implicit
   * {@code :latest} suffix: a bare {@code "qwen3"} matches an installed {@code "qwen3:latest"},
   * while an already-tagged {@code "qwen3:8b"} only matches itself exactly.
   */
  private static boolean matches(String configured, String installed) {
    return installed.equals(configured) || installed.equals(configured + LATEST_SUFFIX);
  }
}
