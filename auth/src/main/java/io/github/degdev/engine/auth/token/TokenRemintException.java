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
package io.github.degdev.engine.auth.token;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a token re-mint request cannot be honoured — no bearer token, a signature/format
 * failure, a token expired beyond the remint grace window, or a session past the staleness cap. The
 * message is intentionally generic, mirroring {@code InvalidCredentialsException}: the caller
 * learns only that it must log in again, not precisely why. Maps to HTTP 401 via {@link
 * ResponseStatus}.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenRemintException extends RuntimeException {

  public TokenRemintException() {
    super("Token could not be renewed; please log in again");
  }
}
