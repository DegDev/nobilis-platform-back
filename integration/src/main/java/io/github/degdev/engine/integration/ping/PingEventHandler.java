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

import io.github.degdev.engine.common.bus.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slice-1 proof-of-pipe consumer: logs every event received on {@link #TOPIC}. Only mounted when
 * the worker has opted into the Kafka adapter ({@code nobilis.integration.bus=kafka}).
 */
@Component
@ConditionalOnProperty(prefix = "nobilis.integration", name = "bus", havingValue = "kafka")
public class PingEventHandler implements EventHandler {

  static final String TOPIC = "nobilis.integration.ping";

  private static final Logger LOG = LoggerFactory.getLogger(PingEventHandler.class);

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void handle(String payload) {
    LOG.info("Received ping event: {}", payload);
  }
}
