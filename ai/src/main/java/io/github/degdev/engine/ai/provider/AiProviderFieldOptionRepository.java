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
 * Spring Data repository for {@link AiProviderFieldOption} rows. Needed starting slice 2: {@code
 * AiProviderDefaults} attaches a field's options to its {@code AiFieldDescriptor} for {@code
 * SELECT}/{@code MULTISELECT} types (empty for every other type).
 */
public interface AiProviderFieldOptionRepository
    extends JpaRepository<AiProviderFieldOption, Long> {

  /**
   * A field's selectable options, in display order.
   *
   * @param fieldId the owning {@link AiProviderField#getId()}
   * @return the matching options, ordered by {@code sortOrder}
   */
  List<AiProviderFieldOption> findByFieldIdOrderBySortOrder(Long fieldId);
}
