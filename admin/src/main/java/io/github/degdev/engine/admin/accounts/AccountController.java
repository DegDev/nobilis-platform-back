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

import io.github.degdev.engine.admin.api.NotFoundException;
import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.account.AccountDto;
import io.github.degdev.engine.auth.account.AccountService;
import io.github.degdev.engine.auth.role.EnginePermissions;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts management — the third screen on the admin REST framework, following the {@code
 * RoleController} shape: RFC 9457 errors ({@code GlobalExceptionHandler}), {@code Pageable}/{@code
 * PagedModel} pagination, and {@link RequiresPermission} at the class level so every handler needs
 * {@link EnginePermissions#ACCOUNT_MANAGE}.
 *
 * <p>Manages EXISTING accounts only — there is no create ({@code POST}) because a created account
 * cannot authenticate until the DB-login milestone. There is no {@code DELETE} either: a "delete"
 * is a soft one, expressed as {@code PUT} with {@code status = BLOCKED} through {@link #update}, so
 * the status transitions are exposed directly rather than behind a delete verb. The {@code {id}}
 * paths are constrained to digits, consistent with the roles controller.
 *
 * <p><b>Mounted only with a store.</b> Like the roles controller this is a {@code @RestController}
 * EXCLUDED from the host component scan and registered as a {@code @Bean} by {@code
 * AccountAdminAutoConfiguration}, gated on an {@link AccountService} existing — i.e. only when the
 * database is active. The stateless default host mounts no accounts endpoints.
 */
@RestController
@RequestMapping("/admin/api/accounts")
@RequiresPermission(EnginePermissions.ACCOUNT_MANAGE)
public class AccountController {

  private final AccountService accountService;

  /**
   * Creates the controller.
   *
   * @param accountService the account management service
   */
  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  /**
   * Lists accounts, one page at a time.
   *
   * @param pageable the page request ({@code ?page=&size=&sort=})
   * @return a page of accounts as {@link AccountDto}s
   */
  @GetMapping
  public PagedModel<AccountDto> list(Pageable pageable) {
    return new PagedModel<>(accountService.list(pageable));
  }

  /**
   * Reads one account by id.
   *
   * @param id the account id
   * @return the account as an {@link AccountDto}
   * @throws NotFoundException if no account has that id
   */
  @GetMapping("/{id:\\d+}")
  public AccountDto get(@PathVariable Long id) {
    return accountService
        .get(id)
        .orElseThrow(() -> new NotFoundException("No account with id " + id));
  }

  /**
   * Updates an account's status, realms and roles (a soft delete is {@code status = BLOCKED}).
   *
   * @param id the account id
   * @param request the validated update request
   * @return the updated account
   * @throws NotFoundException if no account has that id
   */
  @PutMapping("/{id:\\d+}")
  public AccountDto update(
      @PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
    return accountService
        .update(id, request.status(), request.realms(), request.roleIds())
        .orElseThrow(() -> new NotFoundException("No account with id " + id));
  }
}
