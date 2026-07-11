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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import io.github.degdev.engine.common.notifications.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Send-only Telegram transport (slice 4), no real HTTP — {@link MockRestServiceServer} stubs the
 * Bot API. Mirrors what {@link NotificationDispatchEventHandlerTest} already proves for the
 * dispatcher: the three failure classes (success, terminal 4xx, retriable 5xx).
 */
class TelegramNotificationTransportTest {

  private static final String BASE_URL = "https://api.telegram.org/bottest-token";

  private MockRestServiceServer server;
  private TelegramNotificationTransport transport;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    transport = new TelegramNotificationTransport(builder.build());
  }

  @Test
  void declaresTelegramAsItsChannel() {
    assertThat(transport.transport()).isEqualTo(Transport.TELEGRAM);
  }

  @Test
  void successfulSendThrowsNothing() {
    server
        .expect(requestTo(BASE_URL + "/sendMessage"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    transport.send("123456789", null, "Order created");

    server.verify();
  }

  @Test
  void clientErrorIsTerminal() {
    server
        .expect(requestTo(BASE_URL + "/sendMessage"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body(
                    "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not"
                        + " found\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> transport.send("bad-chat-id", null, "Order created"));

    server.verify();
  }

  @Test
  void serverErrorIsRetriable() {
    server
        .expect(requestTo(BASE_URL + "/sendMessage"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatExceptionOfType(RetriableBusException.class)
        .isThrownBy(() -> transport.send("123456789", null, "Order created"));

    server.verify();
  }
}
