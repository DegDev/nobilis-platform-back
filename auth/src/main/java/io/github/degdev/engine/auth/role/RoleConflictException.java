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
package io.github.degdev.engine.auth.role;

import io.github.degdev.engine.common.i18n.MessageKeyException;

/**
 * Signals that a role operation conflicts with the current state — a duplicate {@code code} on
 * create, or deleting a role still assigned to accounts. A domain signal, deliberately free of any
 * HTTP/web type; the admin layer maps it to an RFC 9457 {@code 409 Conflict}. The message names the
 * blocker and is safe to surface.
 */
public class RoleConflictException extends RuntimeException implements MessageKeyException {

  private final String messageKey;
  private final Object[] messageArguments;

  /**
   * Creates the exception.
   *
   * @param messageKey the message-bundle key
   * @param messageArguments values interpolated into the localized message
   */
  public RoleConflictException(String messageKey, Object... messageArguments) {
    super(messageKey);
    this.messageKey = messageKey;
    this.messageArguments = messageArguments.clone();
  }

  @Override
  public String messageKey() {
    return messageKey;
  }

  @Override
  public Object[] messageArguments() {
    return messageArguments.clone();
  }
}
