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

/**
 * A provider-agnostic chat-completion result — {@link LlmClient}'s output. The telemetry fields are
 * nullable and adapter-dependent (Ollama's native wire reports them; a future OpenAI-compat adapter
 * may not) — captured here for a future consumer (BL-005, token-cost/usage) but not required by
 * this slice, which only needs {@code content}.
 *
 * @param content the completion text
 * @param promptEvalCount tokens evaluated from the prompt, if the provider reports it
 * @param evalCount tokens generated in the reply, if the provider reports it
 * @param doneReason why generation stopped (e.g. {@code "stop"}, {@code "length"}), if reported
 * @param thinking a think-model's reasoning output, if the provider reports it and suppression
 *     ({@link LlmRequest#noThink()}) was not applied — captured, not required to be used yet
 */
public record LlmResponse(
    String content,
    Integer promptEvalCount,
    Integer evalCount,
    String doneReason,
    String thinking) {}
