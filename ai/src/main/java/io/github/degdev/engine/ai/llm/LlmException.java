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
 * Thrown by {@link LlmClient#complete(LlmRequest)} when the provider rejects the request, is
 * unreachable, times out, or returns a malformed/error response (including Ollama's 200-OK-with-
 * error-body case). A plain domain exception carrying a clear human message — this slice has no bus
 * context yet, so terminal/retriable classification is deferred to slice 6, when a consumer rides
 * the event bus.
 */
public class LlmException extends RuntimeException {

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmException(String message) {
    super(message);
  }
}
