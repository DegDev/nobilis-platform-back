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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Contributes {@link LlmClient} (as {@link OllamaLlmClient}) and {@link OllamaHealthCheckService}
 * when a base URL is configured — mirrors {@code CryptoAutoConfiguration}'s opt-in-on-property
 * shape, not {@code AiServiceAutoConfiguration}'s JPA gate (this client has no DB dependency,
 * deliberately a SEPARATE autoconfig so a host can use the LLM client without ever mounting the
 * profile-persistence layer, or vice versa).
 *
 * <p>Gated on {@code nobilis.ai.base-url} rather than unconditional: a host with no Ollama endpoint
 * configured simply has no {@link LlmClient}, never a fail-fast boot (mirrors {@code
 * CryptoAutoConfiguration}'s master-key gate). Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nobilis.ai", name = "base-url")
@EnableConfigurationProperties(AiProperties.class)
public class AiLlmAutoConfiguration {

  /**
   * Provides the LLM client when a base URL is configured. Declared as the port type ({@link
   * LlmClient}), not the concrete adapter — a future second provider swaps the implementation
   * without changing what callers inject.
   *
   * @param properties holds the configured base URL
   * @return the Ollama-backed LLM client
   */
  @Bean
  public LlmClient llmClient(AiProperties properties) {
    return new OllamaLlmClient(RestClient.builder().baseUrl(properties.baseUrl()).build());
  }

  /**
   * Provides the health-check probe when a base URL is configured.
   *
   * @param properties holds the configured base URL
   * @return the Ollama health-check probe
   */
  @Bean
  public OllamaHealthCheckService ollamaHealthCheckService(AiProperties properties) {
    return new OllamaHealthCheckService(RestClient.builder().baseUrl(properties.baseUrl()).build());
  }
}
