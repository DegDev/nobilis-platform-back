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

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Test-only proof that the scheduling harness ({@link SchedulingConfiguration}) actually runs live:
 * logs a tick on a fixed delay. Off by default — opt in with {@code
 * nobilis.integration.scheduling.demo-tick.enabled=true}, independent of the {@code
 * nobilis.integration.scheduling.enabled} flag that activates {@code @Scheduled} itself (enabling
 * scheduling for a future real job does not also start this demo). Not a real periodic job — no
 * domain sweeper logic, see milestone 07.
 */
@Component
@ConditionalOnProperty(prefix = "nobilis.integration.scheduling.demo-tick", name = "enabled")
class DemoTickJob {

  private static final Logger LOG = LoggerFactory.getLogger(DemoTickJob.class);

  private final AtomicLong tickCount = new AtomicLong();

  @Scheduled(fixedDelayString = "${nobilis.integration.scheduling.demo-tick.fixed-delay:60000}")
  void tick() {
    long count = tickCount.incrementAndGet();
    LOG.info("Scheduler demo tick #{}", count);
  }

  long tickCount() {
    return tickCount.get();
  }
}
