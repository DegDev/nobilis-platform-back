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

import java.util.List;

/**
 * A provider-agnostic chat-completion request — {@link LlmClient}'s input. Param names follow the
 * engine's Ollama-native catalog fields (Fork 5: {@code temperature}/{@code top_p}/{@code
 * num_predict}, never an OpenAI-compat {@code max_tokens} translation), but the shape itself
 * carries no Ollama-specific wire detail; an adapter maps it onto whatever the concrete provider
 * expects.
 *
 * @param model the model name to complete against
 * @param messages the conversation so far, oldest first
 * @param temperature sampling temperature (nullable — provider default applies if absent)
 * @param topP nucleus-sampling cutoff (nullable)
 * @param numPredict the max number of tokens to generate (nullable)
 * @param noThink whether to suppress a think-model's reasoning output (nullable — {@code true} maps
 *     to Ollama's native top-level {@code think:false}; {@code false}/{@code null} allows
 *     reasoning, matching the catalog's {@code no-think} field)
 */
public record LlmRequest(
    String model,
    List<Message> messages,
    Double temperature,
    Double topP,
    Integer numPredict,
    Boolean noThink) {}
