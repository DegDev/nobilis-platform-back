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
package io.github.degdev.engine.ai.profile;

import io.github.degdev.engine.common.i18n.MessageKeyException;

/**
 * Signals that an AI-profile save/resolve request conflicts with the catalog: an unknown provider,
 * an unknown field key, a value outside the field's bounds, a secret field submitted as a plain
 * param, or a purpose with no default provider configured. A domain signal, deliberately free of
 * any HTTP/web type; the admin layer (a later slice) maps it to an RFC 9457 {@code 400}. Mirrors
 * {@code RoleConflictException}'s flexible {@code messageKey}/{@code messageArguments} shape rather
 * than one subtype per failure mode.
 */
public class AiProfileException extends RuntimeException implements MessageKeyException {

  private final String messageKey;
  private final Object[] messageArguments;

  /**
   * Creates the exception.
   *
   * @param messageKey the message-bundle key
   * @param messageArguments values interpolated into the localized message
   */
  public AiProfileException(String messageKey, Object... messageArguments) {
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
