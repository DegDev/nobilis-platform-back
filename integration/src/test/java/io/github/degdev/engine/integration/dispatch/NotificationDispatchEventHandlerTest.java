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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.common.bus.RetriableBusException;
import io.github.degdev.engine.common.bus.TerminalBusException;
import io.github.degdev.engine.common.notifications.NotificationTemplateTranslation;
import io.github.degdev.engine.common.notifications.NotificationsService;
import io.github.degdev.engine.common.notifications.Transport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Slice-3: {@link NotificationDispatchEventHandler} must throw typed exceptions instead of
 * swallowing failures, so the bus-level retry/DLQ machinery (see {@code
 * KafkaEventBusAutoConfiguration}) can act on them.
 *
 * <p>Slice-4: transport selection is a {@code Map} built from every registered {@link
 * NotificationTransport}, keyed by each adapter's own {@link NotificationTransport#transport()} —
 * proven by registering two transports and routing by the event's channel, and by a channel with no
 * registered adapter.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchEventHandlerTest {

  private static final String VALID_PAYLOAD =
      """
      {"typeKey":"order.created","locale":"ru","transport":"EMAIL",\
      "recipient":"customer@example.test","vars":null}\
      """;

  @Mock private NotificationsService notificationsService;
  @Mock private NotificationTransport transport;
  @Mock private NotificationTemplateTranslation translation;

  private NotificationDispatchEventHandler dispatcher;

  @BeforeEach
  void setUp() {
    when(transport.transport()).thenReturn(Transport.EMAIL);
    dispatcher =
        new NotificationDispatchEventHandler(
            notificationsService, List.of(transport), new JsonMapper());
  }

  @Test
  void unparseablePayloadIsTerminal() {
    verifyNoInteractions(notificationsService);

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> dispatcher.handle("not json"));

    verify(transport, never()).send(any(), any(), any());
  }

  @Test
  void noMatchingTemplateIsTerminal() {
    when(notificationsService.resolveForDispatch("order.created", Transport.EMAIL, "ru"))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> dispatcher.handle(VALID_PAYLOAD));
  }

  @Test
  void transportFailureIsRetriable() {
    when(notificationsService.resolveForDispatch("order.created", Transport.EMAIL, "ru"))
        .thenReturn(Optional.of(translation));
    when(translation.getSubject()).thenReturn("Заказ создан");
    when(translation.getBody()).thenReturn("Спасибо за заказ!");
    doThrow(new RuntimeException("SMTP unavailable")).when(transport).send(any(), any(), any());

    assertThatExceptionOfType(RetriableBusException.class)
        .isThrownBy(() -> dispatcher.handle(VALID_PAYLOAD));
  }

  @Test
  void successfulDeliveryThrowsNothing() {
    when(notificationsService.resolveForDispatch("order.created", Transport.EMAIL, "ru"))
        .thenReturn(Optional.of(translation));
    when(translation.getSubject()).thenReturn("Заказ создан");
    when(translation.getBody()).thenReturn("Спасибо за заказ!");

    dispatcher.handle(VALID_PAYLOAD);

    assertThat(dispatcher.topic()).isEqualTo(NotificationDispatchEventHandler.TOPIC);
  }

  @Test
  void channelWithNoRegisteredTransportIsTerminal() {
    String telegramPayload =
        """
        {"typeKey":"order.created","locale":"ru","transport":"TELEGRAM",\
        "recipient":"chat123","vars":null}\
        """;
    when(notificationsService.resolveForDispatch("order.created", Transport.TELEGRAM, "ru"))
        .thenReturn(Optional.of(translation));

    assertThatExceptionOfType(TerminalBusException.class)
        .isThrownBy(() -> dispatcher.handle(telegramPayload));
  }

  @Test
  void routesToTheMatchingTransportWhenTwoAreRegistered() {
    NotificationTransport telegramTransport = mock(NotificationTransport.class);
    when(telegramTransport.transport()).thenReturn(Transport.TELEGRAM);
    NotificationDispatchEventHandler twoTransportDispatcher =
        new NotificationDispatchEventHandler(
            notificationsService, List.of(transport, telegramTransport), new JsonMapper());
    when(notificationsService.resolveForDispatch("order.created", Transport.EMAIL, "ru"))
        .thenReturn(Optional.of(translation));
    when(translation.getSubject()).thenReturn("Заказ создан");
    when(translation.getBody()).thenReturn("Спасибо за заказ!");

    twoTransportDispatcher.handle(VALID_PAYLOAD);

    verify(transport).send("customer@example.test", "Заказ создан", "Спасибо за заказ!");
    verify(telegramTransport, never()).send(any(), any(), any());
  }
}
