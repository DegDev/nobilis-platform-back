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
import static org.awaitility.Awaitility.await;

import io.github.degdev.engine.common.notifications.NotificationEvent;
import io.github.degdev.engine.common.notifications.NotificationsService;
import io.github.degdev.engine.common.notifications.Transport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trip slice: {@link NotificationDispatchEventHandler#handle} → {@link
 * NotificationsService#resolveForDispatch} (real Postgres) → {@link EmailNotificationTransport}
 * (real SMTP against a Testcontainers Mailpit), asserted via Mailpit's HTTP API. Proves the
 * milestone-04 slice-2 DoD: event in, email delivered out. Invokes the dispatcher's {@code
 * handle(String)} directly rather than round-tripping through Kafka — the bus→handler wiring is
 * already proven by {@code KafkaEventBusIntegrationTest}; this slice is about template resolution +
 * transport delivery, not the bus itself.
 */
@SpringBootTest
@Testcontainers
class NotificationDispatchEventHandlerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Container
  static GenericContainer<?> mailpit =
      new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025, 8025);

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Autowired private NotificationsService notificationsService;
  @Autowired private NotificationDispatchEventHandler dispatcher;
  @Autowired private JsonMapper jsonMapper;

  @DynamicPropertySource
  static void mailProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", mailpit::getHost);
    registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  @Test
  void dispatchesAMatchingEventAsAnEmailDeliveredToMailpit() throws Exception {
    notificationsService.createType("order.created", true, null);
    notificationsService.createTemplate("order.created", Transport.EMAIL);
    notificationsService.upsertTranslation(
        "order.created", Transport.EMAIL, "ru", "Заказ создан", "Спасибо за заказ!");

    NotificationEvent event =
        new NotificationEvent(
            "order.created", "ru", Transport.EMAIL, "customer@example.test", null);
    dispatcher.handle(jsonMapper.writeValueAsString(event));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              JsonNode messages = fetchMailpitMessages();
              assertThat(messages.path("messages")).hasSize(1);
              JsonNode message = messages.path("messages").get(0);
              assertThat(message.path("Subject").asText()).isEqualTo("Заказ создан");
              assertThat(message.path("To").get(0).path("Address").asText())
                  .isEqualTo("customer@example.test");
            });
  }

  private JsonNode fetchMailpitMessages() throws Exception {
    URI uri =
        URI.create(
            "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages");
    HttpResponse<String> response =
        HTTP_CLIENT.send(
            HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    return jsonMapper.readTree(response.body());
  }
}
