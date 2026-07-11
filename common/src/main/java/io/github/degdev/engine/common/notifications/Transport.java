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

/**
 * The transport channel a {@link NotificationTemplate} renders for. An engine-level mechanism: the
 * fixed set of adapters the milestone-04 dispatcher ships. Persisted as {@code varchar} via
 * {@code @Enumerated(STRING)}, never a native Postgres enum. Domain products select from these;
 * they cannot define new transports at runtime (a new adapter is code, not data).
 */
public enum Transport {

  /** Email transport (SMTP via Mailpit in dev). Templates typically use both subject and body. */
  EMAIL,

  /** Telegram transport (Bot API). Templates typically use body only (subject is ignored). */
  TELEGRAM,

  /** SMS transport. Templates typically use body only (subject is ignored). */
  SMS
}
