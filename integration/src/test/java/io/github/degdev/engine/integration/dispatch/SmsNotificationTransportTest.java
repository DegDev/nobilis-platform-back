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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import io.github.degdev.engine.common.notifications.Transport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Send-only SMS transport (slice 5), no real HTTP — {@link MockRestServiceServer} stubs the
 * Messaggio API. Mirrors {@link TelegramNotificationTransportTest}'s three failure classes
 * (success, terminal 4xx, retriable 5xx), plus a case proving the configured country-code prefix is
 * applied to the recipient.
 */
class SmsNotificationTransportTest {

  private static final String BASE_URL = "https://msg.messaggio.com";

  private MockRestServiceServer server;
  private SmsNotificationTransport transport;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    transport = new SmsNotificationTransport(builder.build(), "test-login", "test-sender", "373");
  }

  @Test
  void declaresSmsAsItsChannel() {
    assertThat(transport.transport()).isEqualTo(Transport.SMS);
  }

  @Test
  void successfulSendPrefixesRecipientWithCountryCodeAndThrowsNothing() {
    server
        .expect(requestTo(BASE_URL + "/api/v1/send"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Messaggio-Login", "test-login"))
        .andExpect(request -> assertThat(bodyOf(request)).contains("\"phone\":\"37369123456\""))
        .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

    transport.send("69123456", null, "Order created");

    server.verify();
  }

  @Test
  void clientErrorIsTerminal() {
    server
        .expect(requestTo(BASE_URL + "/api/v1/send"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"invalid phone\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> transport.send("bad-phone", null, "Order created"));

    server.verify();
  }

  @Test
  void serverErrorIsRetriable() {
    server
        .expect(requestTo(BASE_URL + "/api/v1/send"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatExceptionOfType(RetriableBusException.class)
        .isThrownBy(() -> transport.send("69123456", null, "Order created"));

    server.verify();
  }

  private static String bodyOf(org.springframework.http.client.ClientHttpRequest request) {
    return new String(((MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.UTF_8);
  }
}
