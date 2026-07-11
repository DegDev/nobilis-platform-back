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
package io.github.degdev.engine.common.bus;

/**
 * Broker-neutral publish side of the engine's message bus. Callers depend on this port, never on a
 * broker-specific type (Kafka, RabbitMQ, …) — exactly one adapter is active at a time, selected by
 * config (see {@code nobilis.integration.bus}).
 */
public interface EventBus {

  /**
   * Publishes an event to a topic/channel. Delivery semantics (ordering, retries, durability) are
   * whatever the active adapter provides.
   *
   * @param topic the topic/channel name
   * @param payload the event payload
   */
  void publish(String topic, String payload);
}
