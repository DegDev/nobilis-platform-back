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
 * Ollama's native {@code /api/chat} response {@code message}, distinct from the port-level {@link
 * Message} (which the request side reuses): a think-model's reasoning arrives nested here as {@code
 * thinking}, captured rather than dropped — an Ollama-specific wire detail that must not leak into
 * the provider-agnostic port's {@link Message} type.
 */
record OllamaResponseMessage(String role, String content, String thinking) {}
