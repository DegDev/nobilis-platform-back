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
package io.github.degdev.engine.auth.password;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the always-available {@link PasswordHasher}. BCrypt hashing needs no configuration, so
 * unlike the JWT service this bean is unconditional whenever auth is on the classpath. Discovered
 * via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} —
 * the host application opts in by depending on auth, not by being component-scanned.
 */
@AutoConfiguration
public class PasswordAutoConfiguration {

  /**
   * Provides the BCrypt password hasher.
   *
   * @return a {@link PasswordHasher}
   */
  @Bean
  @ConditionalOnMissingBean
  public PasswordHasher passwordHasher() {
    return new PasswordHasher();
  }
}
