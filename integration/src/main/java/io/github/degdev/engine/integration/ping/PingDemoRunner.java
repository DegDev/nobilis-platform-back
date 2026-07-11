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
package io.github.degdev.engine.integration.ping;

import io.github.degdev.engine.common.bus.EventBus;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test-only producer for the slice-1 round-trip proof: publishes one ping on startup, then {@link
 * PingEventHandler} logs it back. Off by default — opt in with {@code
 * nobilis.integration.ping-demo.enabled=true}. Not a real app/admin producer.
 */
@Component
@ConditionalOnProperty(prefix = "nobilis.integration.ping-demo", name = "enabled")
public class PingDemoRunner implements CommandLineRunner {

  private final EventBus eventBus;

  public PingDemoRunner(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  public void run(String... args) {
    eventBus.publish(PingEventHandler.TOPIC, "ping-" + UUID.randomUUID());
  }
}
