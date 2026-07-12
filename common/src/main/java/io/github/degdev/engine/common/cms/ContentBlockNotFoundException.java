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
package io.github.degdev.engine.common.cms;

import io.github.degdev.engine.common.i18n.MessageKeyException;

/**
 * Signals that a requested content block, or a translation of one, does not exist. A domain signal,
 * deliberately free of any HTTP/web type; the admin layer maps it to an RFC 9457 {@code 404 Not
 * Found}. The message is safe to surface (it describes the missing resource, not internal state).
 */
public class ContentBlockNotFoundException extends RuntimeException implements MessageKeyException {

  private final String messageKey;
  private final Object[] messageArguments;

  /**
   * Creates the exception.
   *
   * @param messageKey the message-bundle key
   * @param messageArguments values interpolated into the localized message
   */
  public ContentBlockNotFoundException(String messageKey, Object... messageArguments) {
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
