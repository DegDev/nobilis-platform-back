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

import io.github.degdev.engine.common.notifications.Transport;

/**
 * Delivery side of a notification transport channel — one adapter per {@link
 * io.github.degdev.engine.common.notifications.Transport}. Mirrors {@link
 * io.github.degdev.engine.common.bus.EventBus}'s single-method port shape: the dispatcher depends
 * on this interface, never on a concrete sender. Every adapter declares the {@link Transport} it
 * handles via {@link #transport()}, so the dispatcher can route by {@code
 * NotificationEvent.transport()} without knowing about concrete adapter types (slice 4 — adding
 * Telegram, the port's second adapter).
 */
public interface NotificationTransport {

  /**
   * Sends one notification.
   *
   * @param recipient the transport-specific address (e.g. an email address)
   * @param subject the subject line (may be {@code null}; ignored by body-only transports)
   * @param body the message body
   */
  void send(String recipient, String subject, String body);

  /**
   * The channel this adapter delivers over.
   *
   * @return the {@link Transport} this adapter handles
   */
  Transport transport();
}
