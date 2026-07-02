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
package io.github.degdev.engine.auth.adminlogin.web;

import io.github.degdev.engine.auth.adminlogin.AdminLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for admin email/password login. Mounted only when {@code
 * nobilis.auth.admin-login.enabled=true} (see {@code AdminLoginAutoConfiguration}); absent
 * otherwise. A successful {@code POST} returns a signed JWT; bad credentials surface as HTTP 401
 * via {@link io.github.degdev.engine.auth.adminlogin.InvalidCredentialsException}.
 */
@RestController
@RequiredArgsConstructor
public class AdminLoginController {

  private final AdminLoginService adminLoginService;

  /**
   * Authenticates the admin and returns a token.
   *
   * @param request the submitted email and password
   * @return the issued token wrapped in a {@link LoginResponse}
   */
  @PostMapping("/auth/admin/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    return new LoginResponse(adminLoginService.login(request.email(), request.password()));
  }
}
