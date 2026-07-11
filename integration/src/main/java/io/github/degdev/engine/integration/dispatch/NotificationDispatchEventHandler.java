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
import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import io.github.degdev.engine.common.notifications.NotificationEvent;
import io.github.degdev.engine.common.notifications.NotificationTemplateTranslation;
import io.github.degdev.engine.common.notifications.NotificationsService;
import io.github.degdev.engine.common.notifications.Transport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The notification dispatcher: consumes a {@link NotificationEvent} off {@link #TOPIC}, resolves
 * the matching template translation via {@link NotificationsService#resolveForDispatch}, and
 * delivers it through the {@link NotificationTransport} matching the event's {@link
 * NotificationEvent#transport()}. Broker-neutral, like every {@link EventHandler} — never depends
 * on Kafka directly, so it stays mounted regardless of which bus adapter (if any) is active.
 *
 * <p>Transport selection (slice 4): every registered {@link NotificationTransport} bean declares
 * its own {@link NotificationTransport#transport()}; this handler collects them into a {@code Map}
 * keyed by that value, so adding a new adapter (Telegram, SMS, …) never requires editing this
 * class. A {@link NotificationEvent} whose transport has no registered adapter (e.g. Telegram
 * configured with no bot token) is {@link TerminalBusException} — the same as any other
 * unresolvable event, never a silent drop.
 *
 * <p>Failure handling (slice 3): an unparseable payload, no matching enabled type/template/
 * translation, or no registered transport are {@link TerminalBusException} — not retried, the
 * active bus adapter dead-letters them directly. A transport delivery failure is {@link
 * RetriableBusException} — the adapter retries with backoff before dead-lettering. Interpolation is
 * still deferred: the template's static subject/body is sent as-is; {@link
 * NotificationEvent#vars()} is unused this slice.
 */
@Component
public class NotificationDispatchEventHandler implements EventHandler {

  static final String TOPIC = "nobilis.notifications.dispatch";

  private final NotificationsService notificationsService;
  private final Map<Transport, NotificationTransport> transportsByChannel;
  private final JsonMapper jsonMapper;

  public NotificationDispatchEventHandler(
      NotificationsService notificationsService,
      List<NotificationTransport> transports,
      JsonMapper jsonMapper) {
    this.notificationsService = notificationsService;
    this.transportsByChannel =
        transports.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    NotificationTransport::transport, Function.identity()));
    this.jsonMapper = jsonMapper;
  }

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
      throw new TerminalBusException("Unparseable notification event: " + payload, e);
    }

    Optional<NotificationTemplateTranslation> translation =
        notificationsService.resolveForDispatch(event.typeKey(), event.transport(), event.locale());
    if (translation.isEmpty()) {
      throw new TerminalBusException(
          "No enabled type/template/translation for typeKey='%s', transport=%s, locale='%s'"
              .formatted(event.typeKey(), event.transport(), event.locale()));
    }

    NotificationTransport transport = transportsByChannel.get(event.transport());
    if (transport == null) {
      throw new TerminalBusException("No registered transport for channel " + event.transport());
    }

    try {
      transport.send(
          event.recipient(), translation.get().getSubject(), translation.get().getBody());
    } catch (RetriableBusException | TerminalBusException e) {
      // Already classified by the transport itself (e.g. Telegram's 4xx-vs-5xx split) — propagate
      // as-is instead of flattening it into the generic retriable fallback below.
      throw e;
    } catch (Exception e) {
      throw new RetriableBusException(
          "Transport delivery failed for recipient='" + event.recipient() + "'", e);
    }
  }
}
