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
package io.github.degdev.engine.common.bus.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.degdev.engine.common.bus.EventBus;
import io.github.degdev.engine.common.bus.EventHandler;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Round-trip slice: publish through {@link EventBus} → Kafka (Testcontainers, KRaft) → the
 * adapter's listener container → a registered {@link EventHandler} receives the payload. Proves the
 * DoD for milestone-04 slice 1. Revert {@code nobilis.integration.bus=kafka} (or the adapter
 * wiring) and this test goes red — no bus beans are created, {@code EventBus} fails to autowire.
 *
 * <p>Postgres is present only because {@code common}'s {@code TestEngineApplication} (the
 * auto-detected {@code @SpringBootConfiguration} for this module's test slices) pulls in JPA/Flyway
 * unconditionally, same as every other {@code common} {@code @SpringBootTest} — this slice has
 * nothing to do with persistence.
 */
@SpringBootTest(properties = "nobilis.integration.bus=kafka")
@Testcontainers
class KafkaEventBusIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

  private static final String TOPIC = "nobilis.test.ping";

  @Autowired private EventBus eventBus;
  @Autowired private CapturingHandler capturingHandler;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("nobilis.integration.bus.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Test
  void publishedEventIsReceivedByTheRegisteredHandler() throws InterruptedException {
    eventBus.publish(TOPIC, "ping-1");

    String received = capturingHandler.received.poll(10, TimeUnit.SECONDS);

    assertThat(received).isEqualTo("ping-1");
  }

  @TestConfiguration
  static class TestHandlerConfiguration {

    @Bean
    CapturingHandler capturingHandler() {
      return new CapturingHandler();
    }
  }

  static class CapturingHandler implements EventHandler {

    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

    @Override
    public String topic() {
      return TOPIC;
    }

    @Override
    public void handle(String payload) {
      received.add(payload);
    }
  }
}
