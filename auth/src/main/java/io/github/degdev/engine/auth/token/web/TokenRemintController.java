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
package io.github.degdev.engine.auth.token.web;

import io.github.degdev.engine.auth.adminlogin.web.LoginResponse;
import io.github.degdev.engine.auth.token.TokenRemintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for silent token re-mint. Mounted whenever {@code nobilis.auth.jwt.secret} is
 * configured (see {@code TokenRemintAutoConfiguration}) — not gated behind admin-login
 * specifically, since re-mint is a token-primitive concern. Exempted from {@code
 * AdminContourFilter}'s claims check the same way {@code /auth/admin/login} is, since a caller
 * presenting an expired token is already anonymous by the time the gate has run.
 */
@RestController
@RequiredArgsConstructor
public class TokenRemintController {

  private final TokenRemintService tokenRemintService;

  /**
   * Re-mints the bearer token presented in the {@code Authorization} header.
   *
   * @param authorization the raw {@code Authorization} header, or {@code null} if absent
   * @return the freshly issued token wrapped in a {@link LoginResponse}
   */
  @PostMapping("/auth/admin/remint")
  public LoginResponse remint(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    return new LoginResponse(tokenRemintService.remint(authorization));
  }
}
