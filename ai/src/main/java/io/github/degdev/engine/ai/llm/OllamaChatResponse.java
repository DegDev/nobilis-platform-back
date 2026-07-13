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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ollama's native {@code POST /api/chat} response body (Fork 5, LOCKED). {@code error} is Ollama's
 * own gotcha: a 200 OK can still carry {@code {"error": "..."}} instead of a {@code message} —
 * {@link OllamaLlmClient} inspects this field explicitly before treating a response as a success,
 * never assuming HTTP 200 alone means a reply arrived. {@code message} is {@link
 * OllamaResponseMessage} (not the port-level {@link Message}) because a think-model's reasoning
 * arrives nested here as {@code message.thinking} when suppression isn't applied.
 */
record OllamaChatResponse(
    OllamaResponseMessage message,
    Boolean done,
    @JsonProperty("done_reason") String doneReason,
    @JsonProperty("prompt_eval_count") Integer promptEvalCount,
    @JsonProperty("eval_count") Integer evalCount,
    String error) {}
