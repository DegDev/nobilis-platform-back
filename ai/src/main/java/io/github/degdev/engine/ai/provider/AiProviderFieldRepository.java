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
package io.github.degdev.engine.ai.provider;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AiProviderField} catalog entries. */
public interface AiProviderFieldRepository extends JpaRepository<AiProviderField, Long> {

  /**
   * The catalog fields for a given purpose/provider pair, in display order — the shape the
   * data-driven admin form renders from.
   *
   * @param purpose the purpose key
   * @param providerCode the provider code
   * @return the matching fields, ordered by {@code sortOrder}
   */
  List<AiProviderField> findByPurposeAndProviderCodeOrderBySortOrder(
      String purpose, String providerCode);
}
