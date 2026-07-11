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
package io.github.degdev.engine.common.bus.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.degdev.engine.common.bus.EventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the opt-in contract: with no {@code nobilis.integration.bus} property, {@link
 * KafkaEventBusAutoConfiguration} contributes no {@link EventBus} — never a fail-fast boot on a
 * host that hasn't selected an adapter. The presence case (bus=kafka DOES get an {@link EventBus}
 * that round-trips through a real broker) is proven by {@link KafkaEventBusIntegrationTest}.
 */
class KafkaEventBusAutoConfigurationTest {

  @Test
  void eventBusIsAbsentWithoutTheBusProperty() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaEventBusAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(EventBus.class));
  }
}
