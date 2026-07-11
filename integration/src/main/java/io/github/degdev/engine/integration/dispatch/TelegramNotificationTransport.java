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

import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import io.github.degdev.engine.common.notifications.Transport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link NotificationTransport} adapter for {@code Transport.TELEGRAM}, send-only — posts to the
 * Telegram Bot API's {@code sendMessage} endpoint via {@link RestClient}. Telegram has no subject
 * concept, so {@code subject} is ignored and only {@code body} becomes the message {@code text}
 * (matches {@link Transport#TELEGRAM}'s own Javadoc: "templates typically use body only").
 *
 * <p>Gated on {@code nobilis.notification.telegram.bot-token} — unlike {@link
 * EmailNotificationTransport}, this adapter is useless without a token, so it mounts only when one
 * is configured; a {@code TELEGRAM} event with no token configured falls through the dispatcher's
 * missing-transport path instead of failing to boot.
 *
 * <p>Inline buttons, callback-query handling, and bot commands are out of scope (milestone 07).
 */
@Service
@ConditionalOnProperty(prefix = "nobilis.notification.telegram", name = "bot-token")
class TelegramNotificationTransport implements NotificationTransport {

  private final RestClient restClient;

  @Autowired
  TelegramNotificationTransport(
      @Value("${nobilis.notification.telegram.bot-token}") String botToken) {
    this(RestClient.builder().baseUrl("https://api.telegram.org/bot" + botToken).build());
  }

  /** Test seam — bypasses the {@code @Value}-driven base URL to point at a mock server. */
  TelegramNotificationTransport(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    try {
      restClient
          .post()
          .uri("/sendMessage")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("chat_id", recipient, "text", body))
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException e) {
      throw new TerminalBusException(
          "Telegram rejected the message for chat_id='" + recipient + "': " + e.getMessage(), e);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new RetriableBusException("Telegram send failed for chat_id='" + recipient + "'", e);
    }
  }

  @Override
  public Transport transport() {
    return Transport.TELEGRAM;
  }
}
