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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM client configuration. {@code base-url} is the one INFRA value that must come from the
 * environment (Fork 6): it varies per deployment (dev vs. prod Ollama host) and gates {@link
 * AiLlmAutoConfiguration}'s boot-time opt-in, so it cannot be DB-stored like the catalog's
 * OPERATIONAL fields (property {@code nobilis.ai.base-url}).
 */
@ConfigurationProperties(prefix = "nobilis.ai")
public record AiProperties(String baseUrl) {}
