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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Ollama's native {@code POST /api/chat} request body (Fork 5, LOCKED) — {@code stream:false} for a
 * single synchronous reply. {@link Message}'s {@code {role, content}} shape matches Ollama's wire
 * shape exactly, so it is reused rather than duplicated as a provider-specific DTO.
 *
 * <p>{@code think} is TOP-LEVEL, a sibling of {@code options}, NOT nested inside it (confirmed live
 * against a real Ollama instance) — think-model suppression (Fork 5 follow-up). Omitted from the
 * wire entirely when {@code null} ({@link JsonInclude}) so an absent/allowed-to-think profile sends
 * exactly the same body shape as before this field existed; only an explicit {@code false}
 * suppresses reasoning.
 */
record OllamaChatRequest(
    String model,
    List<Message> messages,
    boolean stream,
    OllamaOptions options,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean think) {}
