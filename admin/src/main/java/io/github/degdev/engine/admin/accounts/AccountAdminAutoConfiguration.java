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
package io.github.degdev.engine.admin.accounts;

import io.github.degdev.engine.auth.account.AccountService;
import io.github.degdev.engine.auth.account.AccountServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts the accounts screen only when its store exists — the same shape as {@code
 * RoleAdminAutoConfiguration}. {@link AccountController} is a request handler but deliberately NOT
 * a component-scanned {@code @Controller}; this auto-configuration registers it as a {@code @Bean}
 * gated on an {@link AccountService} being present, which happens exactly when the database is
 * active (see {@link AccountServiceAutoConfiguration}). The stateless default host mounts no
 * accounts endpoints.
 *
 * <p>{@link ConditionalOnBean} is reliable here because this is an auto-configuration, evaluated
 * after regular configuration and (via {@code after}) after auth's {@link
 * AccountServiceAutoConfiguration} has had its chance to contribute the service. Registered from
 * admin's {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = AccountServiceAutoConfiguration.class)
@ConditionalOnBean(AccountService.class)
public class AccountAdminAutoConfiguration {

  /**
   * Registers the accounts controller when the store is present.
   *
   * @param accountService the account management service
   * @return the accounts management controller
   */
  @Bean
  public AccountController accountController(AccountService accountService) {
    return new AccountController(accountService);
  }
}
