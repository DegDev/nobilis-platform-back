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
package io.github.degdev.engine.common.persistence;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

/**
 * System-level {@link AuditorAware} stub. No authenticated principal exists yet, so every change is
 * attributed to the {@code system} actor. Real user attribution wires in later; until then this
 * bean simply satisfies the auditing reference. Instantiated as a {@code @Bean} by {@link
 * PersistenceAutoConfiguration} (named {@code systemAuditorAware} to match its auditing reference).
 */
public class SystemAuditorAware implements AuditorAware<String> {

  private static final String SYSTEM_ACTOR = "system";

  /**
   * {@return the {@code system} actor} No principal exists before {@code 02-auth}, so every audited
   * change is attributed to the system until real user resolution replaces this.
   */
  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.of(SYSTEM_ACTOR);
  }
}
