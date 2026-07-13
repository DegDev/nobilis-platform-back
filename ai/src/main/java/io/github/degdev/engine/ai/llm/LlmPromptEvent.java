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
 * Typed payload published on the bus ({@link io.github.degdev.engine.common.bus.EventBus}) to run a
 * prompt through whichever provider is configured for a purpose, and deserialized by the
 * integration worker's {@code LlmPromptEventHandler}. Deliberately the THINNEST possible carrier —
 * an engine-level "run this text through the LLM configured for this purpose" pipe, not a domain
 * event. No result shape, no conversation history, no classifier; a domain consumer (a future
 * milestone) builds its own richer event on top of this mechanism rather than this one growing
 * domain fields.
 *
 * @param purpose the {@link io.github.degdev.engine.ai.profile.AiProfileService#resolve(String)}
 *     purpose to resolve a provider/params for
 * @param prompt the user-turn text to send
 */
public record LlmPromptEvent(String purpose, String prompt) {}
