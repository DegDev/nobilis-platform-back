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
import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Slice-3 round-trip: a handler throwing {@link RetriableBusException} is redelivered per {@link
 * KafkaEventBusProperties#retryAttempts()} before landing on {@code <topic>-dlt}; a handler
 * throwing {@link TerminalBusException} skips retry and is dead-lettered on the first attempt.
 * Proves the {@code KafkaEventBusAutoConfiguration} error-handler wiring (auto-commit disabled,
 * {@code DefaultErrorHandler} + {@code DeadLetterPublishingRecoverer}). The DLT topics are observed
 * by registering ordinary {@link EventHandler} beans for them — the bus's own container consumes
 * whatever topic set its registered handlers name, DLT topics included.
 */
@SpringBootTest(properties = "nobilis.integration.bus=kafka")
@Testcontainers
class KafkaEventBusRetryDlqIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

  @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

  private static final String RETRY_TOPIC = "nobilis.test.retry";
  private static final String TERMINAL_TOPIC = "nobilis.test.terminal";

  @Autowired private EventBus eventBus;
  @Autowired private RetriableHandler retriableHandler;
  @Autowired private TerminalHandler terminalHandler;

  @Autowired
  @Qualifier("retryDltHandler")
  private DltCapturingHandler retryDltHandler;

  @Autowired
  @Qualifier("terminalDltHandler")
  private DltCapturingHandler terminalDltHandler;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("nobilis.integration.bus.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Test
  void retriableFailureIsRedeliveredThenDeadLettered() throws InterruptedException {
    eventBus.publish(RETRY_TOPIC, "retry-me");

    String deadLettered = retryDltHandler.received.poll(30, TimeUnit.SECONDS);

    assertThat(deadLettered).isEqualTo("retry-me");
    assertThat(retriableHandler.attempts.get()).isEqualTo(3);
  }

  @Test
  void terminalFailureSkipsRetryAndIsDeadLetteredImmediately() throws InterruptedException {
    eventBus.publish(TERMINAL_TOPIC, "terminal-me");

    String deadLettered = terminalDltHandler.received.poll(30, TimeUnit.SECONDS);

    assertThat(deadLettered).isEqualTo("terminal-me");
    assertThat(terminalHandler.attempts.get()).isEqualTo(1);
  }

  @TestConfiguration
  static class TestHandlerConfiguration {

    @Bean
    RetriableHandler retriableHandler() {
      return new RetriableHandler();
    }

    @Bean
    TerminalHandler terminalHandler() {
      return new TerminalHandler();
    }

    @Bean
    DltCapturingHandler retryDltHandler() {
      return new DltCapturingHandler(RETRY_TOPIC + "-dlt");
    }

    @Bean
    DltCapturingHandler terminalDltHandler() {
      return new DltCapturingHandler(TERMINAL_TOPIC + "-dlt");
    }
  }

  static class RetriableHandler implements EventHandler {

    final AtomicInteger attempts = new AtomicInteger();

    @Override
    public String topic() {
      return RETRY_TOPIC;
    }

    @Override
    public void handle(String payload) {
      attempts.incrementAndGet();
      throw new RetriableBusException("simulated transient failure");
    }
  }

  static class TerminalHandler implements EventHandler {

    final AtomicInteger attempts = new AtomicInteger();

    @Override
    public String topic() {
      return TERMINAL_TOPIC;
    }

    @Override
    public void handle(String payload) {
      attempts.incrementAndGet();
      throw new TerminalBusException("simulated unrecoverable failure");
    }
  }

  static class DltCapturingHandler implements EventHandler {

    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final String topic;

    DltCapturingHandler(String topic) {
      this.topic = topic;
    }

    @Override
    public String topic() {
      return topic;
    }

    @Override
    public void handle(String payload) {
      received.add(payload);
    }
  }
}
