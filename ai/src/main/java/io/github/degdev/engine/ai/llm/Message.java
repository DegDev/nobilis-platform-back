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
 * One turn in a chat-style prompt. The {@code {role, content}} shape is common to every provider
 * this port anticipates (Ollama's native wire and OpenAI-compat both use it), so this same record
 * doubles as the wire shape in {@link OllamaLlmClient} — no separate provider-specific DTO needed.
 *
 * @param role who is speaking (e.g. {@code "system"}, {@code "user"}, {@code "assistant"})
 * @param content the message text
 */
public record Message(String role, String content) {}
