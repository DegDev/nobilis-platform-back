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

import io.github.degdev.engine.common.bus.EventBus;
import org.springframework.kafka.core.KafkaTemplate;

/** {@link EventBus} implemented over a Kafka {@link KafkaTemplate}. */
class KafkaEventBus implements EventBus {

  private final KafkaTemplate<String, String> kafkaTemplate;

  KafkaEventBus(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void publish(String topic, String payload) {
    kafkaTemplate.send(topic, payload);
  }
}
