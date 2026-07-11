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
package io.github.degdev.engine.integration.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Deterministic proof of the demo job's own logic — invokes {@link DemoTickJob#tick()} directly, no
 * live {@code @Scheduled} trigger and no Awaitility (neither is used elsewhere in this repo).
 * Whether {@code @EnableScheduling} itself fires a live cron/fixed-delay tick is a well-established
 * Spring Framework feature, not re-proven here.
 */
class DemoTickJobTest {

  @Test
  void tickIncrementsTheCounterEachInvocation() {
    DemoTickJob job = new DemoTickJob();

    job.tick();
    job.tick();

    assertThat(job.tickCount()).isEqualTo(2);
  }
}
