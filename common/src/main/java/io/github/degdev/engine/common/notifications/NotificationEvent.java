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
package io.github.degdev.engine.common.notifications;

import java.util.Map;

/**
 * Typed payload published on the bus ({@link io.github.degdev.engine.common.bus.EventBus}) to
 * request a notification dispatch, and deserialized by the integration worker's dispatcher. Lives
 * in {@code notifications} rather than {@code bus} so the broker-neutral bus package stays free of
 * a dependency on {@link Transport} — the bus itself only ever sees the serialized JSON form.
 *
 * @param typeKey the {@link NotificationType#getKey()} to dispatch
 * @param locale the requested locale (falls back to {@code ru} per {@code LocaleResolver} if
 *     unresolved)
 * @param transport the delivery channel
 * @param recipient the transport-specific address (e.g. an email address)
 * @param vars reserved for future template-variable interpolation; unused this slice
 */
public record NotificationEvent(
    String typeKey,
    String locale,
    Transport transport,
    String recipient,
    Map<String, String> vars) {}
