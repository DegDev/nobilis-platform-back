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
package io.github.degdev.engine.auth.account;

import io.github.degdev.engine.common.i18n.MessageKeyException;
import java.util.Set;

/**
 * Signals that an account update named a realm the engine does not define. A plain domain exception
 * carrying no HTTP/web type; the admin layer maps it to an RFC 9457 {@code 400}. Mirrors {@code
 * UnknownPermissionException} for roles.
 */
public class UnknownRealmException extends RuntimeException implements MessageKeyException {

  private final Set<String> unknownRealms;

  /**
   * Creates the exception naming the offending realm values.
   *
   * @param unknownRealms the submitted realm values the engine does not define
   */
  public UnknownRealmException(Set<String> unknownRealms) {
    super("Unknown realm(s): " + unknownRealms);
    this.unknownRealms = Set.copyOf(unknownRealms);
  }

  @Override
  public String messageKey() {
    return "error.unknown-realms";
  }

  @Override
  public Object[] messageArguments() {
    return new Object[] {unknownRealms};
  }
}
