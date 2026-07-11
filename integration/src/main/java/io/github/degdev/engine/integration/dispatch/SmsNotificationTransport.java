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
import java.util.List;
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
 * {@link NotificationTransport} adapter for {@code Transport.SMS}, send-only — posts to the
 * Messaggio HTTP API's {@code /api/v1/send} endpoint via {@link RestClient}. SMS has no subject
 * concept, so {@code subject} is ignored and only {@code body} becomes the message text (matches
 * {@link Transport#SMS}'s own Javadoc: "templates typically use body only"). Structural clone of
 * {@link TelegramNotificationTransport}, Messaggio in place of the Bot API.
 *
 * <p>Single-vendor adapter (Messaggio is the only gateway in use) — not a second gateway sub-port;
 * a multi-vendor abstraction is added only if/when a second vendor is actually integrated.
 *
 * <p>Gated on both {@code nobilis.notification.sms.messaggio.login} and {@code
 * nobilis.notification.sms.messaggio.sender-id} — Messaggio can't send with either missing, so this
 * adapter mounts only when both are configured; an {@code SMS} event with either absent falls
 * through the dispatcher's missing-transport path instead of failing to boot.
 *
 * <p>Recipient is prefixed with the configured {@code nobilis.notification.sms.country-code}
 * (account/plan-specific, e.g. Moldova's {@code 373}); full E.164 normalization is out of scope
 * (milestone 07). Messaggio response-body error-code parsing is likewise out of scope — no current
 * Messaggio API documentation was available to design a finer taxonomy against, so classification
 * is HTTP-status-only, same as Telegram.
 */
@Service
@ConditionalOnProperty(
    prefix = "nobilis.notification.sms.messaggio",
    name = {"login", "sender-id"})
class SmsNotificationTransport implements NotificationTransport {

  private final RestClient restClient;
  private final String login;
  private final String senderId;
  private final String countryCode;

  @Autowired
  SmsNotificationTransport(
      @Value("${nobilis.notification.sms.messaggio.login}") String login,
      @Value("${nobilis.notification.sms.messaggio.sender-id}") String senderId,
      @Value("${nobilis.notification.sms.messaggio.base-url:https://msg.messaggio.com}")
          String baseUrl,
      @Value("${nobilis.notification.sms.country-code:}") String countryCode) {
    this(RestClient.builder().baseUrl(baseUrl).build(), login, senderId, countryCode);
  }

  /** Test seam — bypasses the {@code @Value}-driven base URL to point at a mock server. */
  SmsNotificationTransport(
      RestClient restClient, String login, String senderId, String countryCode) {
    this.restClient = restClient;
    this.login = login;
    this.senderId = senderId;
    this.countryCode = countryCode;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    String phone = countryCode + recipient;
    try {
      restClient
          .post()
          .uri("/api/v1/send")
          .header("Messaggio-Login", login)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              Map.of(
                  "recipients", List.of(Map.of("phone", phone)),
                  "channels", List.of("sms"),
                  "sms",
                      Map.of(
                          "from",
                          senderId,
                          "content",
                          List.of(Map.of("type", "text", "text", body)))))
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException e) {
      throw new TerminalBusException(
          "Messaggio rejected the message for phone='" + phone + "': " + e.getMessage(), e);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new RetriableBusException("Messaggio send failed for phone='" + phone + "'", e);
    }
  }

  @Override
  public Transport transport() {
    return Transport.SMS;
  }
}
