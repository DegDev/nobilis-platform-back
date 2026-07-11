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
package io.github.degdev.engine.integration.dispatch;

import io.github.degdev.engine.common.bus.EventHandler;
import io.github.degdev.engine.common.notifications.NotificationEvent;
import io.github.degdev.engine.common.notifications.NotificationTemplateTranslation;
import io.github.degdev.engine.common.notifications.NotificationsService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The notification dispatcher: consumes a {@link NotificationEvent} off {@link #TOPIC}, resolves
 * the matching template translation via {@link NotificationsService#resolveForDispatch}, and
 * delivers it through {@link NotificationTransport}. Broker-neutral, like every {@link
 * EventHandler} — never depends on Kafka directly, so it stays mounted regardless of which bus
 * adapter (if any) is active.
 *
 * <p>Failure handling this slice is drop + log at WARN: no template match, a disabled type, or a
 * transport failure — none of these retry or dead-letter. Retry/DLQ is deferred to slice 3, at the
 * bus level (broker redelivery), not a transport-level retry loop here. Interpolation is deferred
 * too: the template's static subject/body is sent as-is; {@link NotificationEvent#vars()} is unused
 * this slice.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatchEventHandler implements EventHandler {

  static final String TOPIC = "nobilis.notifications.dispatch";

  private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchEventHandler.class);

  private final NotificationsService notificationsService;
  private final NotificationTransport transport;
  private final JsonMapper jsonMapper;

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void handle(String payload) {
    NotificationEvent event;
    try {
      event = jsonMapper.readValue(payload, NotificationEvent.class);
    } catch (Exception e) {
      LOG.warn("Dropping unparseable notification event: {}", payload, e);
      return;
    }

    Optional<NotificationTemplateTranslation> translation =
        notificationsService.resolveForDispatch(event.typeKey(), event.transport(), event.locale());
    if (translation.isEmpty()) {
      LOG.warn(
          "Dropping notification event: no enabled type/template/translation for typeKey='{}',"
              + " transport={}, locale='{}'",
          event.typeKey(),
          event.transport(),
          event.locale());
      return;
    }

    try {
      transport.send(
          event.recipient(), translation.get().getSubject(), translation.get().getBody());
    } catch (Exception e) {
      LOG.warn(
          "Dropping notification event: transport delivery failed for recipient='{}'",
          event.recipient(),
          e);
    }
  }
}
