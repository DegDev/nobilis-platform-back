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

/**
 * Spring Data repository for {@link AiProviderPurpose} links. Needed starting slice 2: resolving a
 * purpose with no saved {@code AiProfile} yet falls back to the catalog's default provider for that
 * purpose (lowest {@code sortOrder}).
 */
public interface AiProviderPurposeRepository
    extends JpaRepository<AiProviderPurpose, AiProviderPurposeId> {

  /**
   * The providers offered for a purpose, in display/preference order.
   *
   * @param purpose the purpose key
   * @return the matching links, ordered by {@code sortOrder}
   */
  List<AiProviderPurpose> findByIdPurposeOrderBySortOrder(String purpose);
}
