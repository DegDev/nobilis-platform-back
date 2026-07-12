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
 * A provider-agnostic chat-completion port. {@link OllamaLlmClient} is the only adapter this
 * milestone; the port shape carries no Ollama-specific wire detail, so a future OpenAI-compat
 * adapter (Yandex, Claude) can implement it without a port change.
 */
public interface LlmClient {

  /**
   * Completes a chat-style prompt.
   *
   * @param request the model, conversation, and sampling params
   * @return the completion
   * @throws LlmException if the provider rejects the request, is unreachable, or returns a
   *     malformed/error response
   */
  LlmResponse complete(LlmRequest request);
}
