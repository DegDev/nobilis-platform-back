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
 * The outcome of an {@link OllamaHealthCheckService#check(String)} probe — a human message only,
 * never a raw exception/stack (matches the engine's {@code TerminalBusException}/{@code
 * RetriableBusException} message-not-stack discipline). Unreachable/timeout is {@code ok=false}
 * with a clear message, never a thrown exception — a future on-demand "check" UI action must be
 * able to show "Ollama unreachable" rather than fail with a 500.
 *
 * @param ok whether the provider is reachable and the requested model is installed
 * @param message a human-readable explanation, safe to display as-is
 */
public record AiHealthCheckResult(boolean ok, String message) {}
