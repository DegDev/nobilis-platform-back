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
package io.github.degdev.engine.ai.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AiProfileParam} value rows. */
public interface AiProfileParamRepository extends JpaRepository<AiProfileParam, AiProfileParamId> {

  /**
   * All saved param values for one profile.
   *
   * @param profileId the owning {@link AiProfile#getId()}
   * @return the profile's saved params
   */
  List<AiProfileParam> findByIdProfileId(Long profileId);

  /**
   * Deletes all saved param values for one profile — the first half of the save flow's
   * delete-then-insert replace (arrives with {@code AiProfileService} in the next slice).
   *
   * @param profileId the owning {@link AiProfile#getId()}
   */
  void deleteByIdProfileId(Long profileId);
}
