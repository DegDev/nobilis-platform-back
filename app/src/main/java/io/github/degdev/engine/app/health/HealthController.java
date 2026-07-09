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
package io.github.degdev.engine.app.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The portal host's proof-of-life: an ops-facing liveness probe, and the host's first live
 * endpoint.
 *
 * <p>Hand-rolled rather than delegating to Spring Boot Actuator, which is not on {@code common}'s
 * classpath — a whole starter for one constant response would be a heavier dependency than the
 * capability warrants. It deliberately reports nothing about portal content: the landing page is
 * static frontend markup, so there is no downstream store or CMS whose reachability this could
 * meaningfully aggregate. When the portal grows real dependencies, this is the seam that learns to
 * check them (or gives way to Actuator).
 */
@RestController
public class HealthController {

  /**
   * Reports that the host is up and serving HTTP.
   *
   * @return the liveness status, always {@code UP} — reaching the handler is the assertion
   */
  @GetMapping("/api/health")
  public HealthStatus health() {
    return new HealthStatus("UP");
  }
}
