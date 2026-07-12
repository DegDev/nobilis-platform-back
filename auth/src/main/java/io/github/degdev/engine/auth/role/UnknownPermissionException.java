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
import java.util.Set;

/**
 * Signals that a role was asked to hold a permission the engine does not define (not in {@link
 * EnginePermissions#ALL}). Rejected rather than silently dropped: a role carrying an undefined
 * permission would be a silent authorization bug at the gate. A domain signal with no HTTP/web
 * type; the admin layer maps it to an RFC 9457 {@code 400 Bad Request}.
 */
public class UnknownPermissionException extends RuntimeException implements MessageKeyException {

  private final Set<String> unknownPermissions;

  /**
   * Creates the exception.
   *
   * @param unknownPermissions the submitted permission values the engine does not define
   */
  public UnknownPermissionException(Set<String> unknownPermissions) {
    super("Unknown permission(s): " + unknownPermissions);
    this.unknownPermissions = Set.copyOf(unknownPermissions);
  }

  @Override
  public String messageKey() {
    return "error.unknown-permissions";
  }

  @Override
  public Object[] messageArguments() {
    return new Object[] {unknownPermissions};
  }
}
