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
 * Thrown by an {@link EventHandler} to signal a failure that redelivery cannot resolve (e.g. no
 * matching template, a disabled notification type, an unparseable payload). The active bus adapter
 * routes the event straight to the dead-letter destination without retrying. Broker- neutral:
 * carries no Kafka-specific meaning, so a future adapter (e.g. RabbitMQ) can honor the same signal.
 */
public class TerminalBusException extends RuntimeException {

  public TerminalBusException(String message, Throwable cause) {
    super(message, cause);
  }

  public TerminalBusException(String message) {
    super(message);
  }
}
