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
package io.github.degdev.engine.admin.ai;

import io.github.degdev.engine.ai.provider.AiProvider;

/**
 * The API view of an {@link AiProvider} catalog entry — the provider picker's option shape.
 *
 * @param code the provider's stable code (e.g. {@code "ollama"})
 * @param label the human-readable label
 * @param hint an optional short description
 * @param requiresLocal whether this provider needs a locally-reachable runtime
 */
public record AiProviderDto(String code, String label, String hint, boolean requiresLocal) {

  /**
   * Projects a catalog entity to its API view.
   *
   * @param provider the catalog entry
   * @return the API view
   */
  public static AiProviderDto from(AiProvider provider) {
    return new AiProviderDto(
        provider.getCode(), provider.getLabel(), provider.getHint(), provider.isRequiresLocal());
  }
}
