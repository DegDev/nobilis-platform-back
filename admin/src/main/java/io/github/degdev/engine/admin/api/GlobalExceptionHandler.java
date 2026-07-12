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
package io.github.degdev.engine.admin.api;

import io.github.degdev.engine.auth.account.UnknownRealmException;
import io.github.degdev.engine.auth.account.UnknownRoleException;
import io.github.degdev.engine.auth.role.RoleConflictException;
import io.github.degdev.engine.auth.role.UnknownPermissionException;
import io.github.degdev.engine.common.cms.ContentBlockConflictException;
import io.github.degdev.engine.common.cms.ContentBlockNotFoundException;
import io.github.degdev.engine.common.i18n.MessageKeyException;
import io.github.degdev.engine.common.i18n.UnsupportedLocaleException;
import io.github.degdev.engine.common.notifications.NotificationConflictException;
import io.github.degdev.engine.common.notifications.NotificationTypeNotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The admin REST framework's single error contract: every failure leaves as an RFC 9457 {@link
 * ProblemDetail} ({@code application/problem+json}), so clients parse one shape. Extending {@link
 * ResponseEntityExceptionHandler} means Spring MVC's own exceptions (unreadable body, wrong method,
 * …) already come back as problem+json; this advice adds the engine's cases on top.
 *
 * <ul>
 *   <li>Bean-validation failures ({@code 400}) are enriched with a {@code fieldErrors} array so a
 *       form can map each message to its field.
 *   <li>{@link NotFoundException} &rarr; {@code 404}.
 *   <li>{@link ForbiddenException} (missing permission, from the interceptor) &rarr; {@code 403}.
 *   <li>{@link RoleConflictException} (duplicate code, or a role still in use) &rarr; {@code 409}.
 *   <li>{@link UnknownPermissionException} (a role given an undefined permission) &rarr; {@code
 *       400}.
 *   <li>{@link UnknownRealmException} / {@link UnknownRoleException} (an account update naming an
 *       undefined realm or an unknown role id) &rarr; {@code 400}.
 *   <li>{@link ContentBlockConflictException} (duplicate content block key) &rarr; {@code 409}.
 *   <li>{@link ContentBlockNotFoundException} (unknown content block or translation) &rarr; {@code
 *       404}.
 *   <li>{@link UnsupportedLocaleException} (a translation write naming a blank or unsupported
 *       locale) &rarr; {@code 400}.
 *   <li>Anything else &rarr; {@code 500} with a generic detail (the real cause is logged, never
 *       leaked to the client).
 * </ul>
 *
 * <p>This covers the MVC layer only. The servlet-layer contour ({@code 401}/{@code 403} for
 * anonymous/wrong-realm) rejects before dispatch and is out of this advice's reach by design.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = ex.getBody();
    body.setDetail(message("validation.failed"));
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null
                            ? message("validation.invalid")
                            : error.getDefaultMessage()))
            .toList();
    body.setProperty("fieldErrors", fieldErrors);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /**
   * Maps a missing resource to {@code 404}.
   *
   * @param ex the not-found signal
   * @return a {@code 404} problem detail carrying the exception's (safe) message
   */
  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message(ex));
  }

  /**
   * Maps a missing permission to {@code 403}.
   *
   * @param ex the forbidden signal
   * @return a {@code 403} problem detail with a non-revealing message
   */
  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, message(ex));
  }

  /**
   * Maps a state conflict — a duplicate role code, or a role still assigned to accounts — to {@code
   * 409}.
   *
   * @param ex the conflict signal
   * @return a {@code 409} problem detail naming the blocker
   */
  @ExceptionHandler(RoleConflictException.class)
  public ProblemDetail handleConflict(RoleConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message(ex));
  }

  /**
   * Maps a request naming a permission the engine does not define to {@code 400}.
   *
   * @param ex the unknown-permission signal
   * @return a {@code 400} problem detail naming the offending permissions
   */
  @ExceptionHandler(UnknownPermissionException.class)
  public ProblemDetail handleUnknownPermission(UnknownPermissionException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(ex));
  }

  /**
   * Maps an account update naming a realm the engine does not define to {@code 400}.
   *
   * @param ex the unknown-realm signal
   * @return a {@code 400} problem detail naming the offending realms
   */
  @ExceptionHandler(UnknownRealmException.class)
  public ProblemDetail handleUnknownRealm(UnknownRealmException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(ex));
  }

  /**
   * Maps an account update referencing a role id that resolves to no role to {@code 400}.
   *
   * @param ex the unknown-role signal
   * @return a {@code 400} problem detail naming the offending role ids
   */
  @ExceptionHandler(UnknownRoleException.class)
  public ProblemDetail handleUnknownRole(UnknownRoleException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(ex));
  }

  /**
   * Maps a duplicate content block key on create to {@code 409}.
   *
   * @param ex the conflict signal
   * @return a {@code 409} problem detail naming the blocker
   */
  @ExceptionHandler(ContentBlockConflictException.class)
  public ProblemDetail handleContentBlockConflict(ContentBlockConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message(ex));
  }

  /**
   * Maps a request naming an unknown content block or translation to {@code 404}.
   *
   * @param ex the not-found signal
   * @return a {@code 404} problem detail carrying the exception's (safe) message
   */
  @ExceptionHandler(ContentBlockNotFoundException.class)
  public ProblemDetail handleContentBlockNotFound(ContentBlockNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message(ex));
  }

  /**
   * Maps a translation write (CMS or notifications) naming a blank or unsupported locale to {@code
   * 400}. Single handler for the shared {@link UnsupportedLocaleException}.
   *
   * @param ex the unsupported-locale signal
   * @return a {@code 400} problem detail naming the rejected locale
   */
  @ExceptionHandler(UnsupportedLocaleException.class)
  public ProblemDetail handleUnsupportedLocale(UnsupportedLocaleException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(ex));
  }

  /**
   * Maps a notification state conflict (duplicate type key or duplicate type+transport template) to
   * {@code 409}.
   */
  @ExceptionHandler(NotificationConflictException.class)
  public ProblemDetail handleNotificationConflict(NotificationConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message(ex));
  }

  /** Maps a request naming an unknown notification type/template/translation to {@code 404}. */
  @ExceptionHandler(NotificationTypeNotFoundException.class)
  public ProblemDetail handleNotificationTypeNotFound(NotificationTypeNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message(ex));
  }

  /**
   * Last-resort handler: turns any otherwise-unhandled exception into a {@code 500} without leaking
   * the cause. The stack trace is logged server-side.
   *
   * @param ex the unexpected failure
   * @return a generic {@code 500} problem detail
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    LOG.error("Unhandled exception in admin API", ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, message("error.unexpected"));
  }

  private String message(MessageKeyException exception) {
    return messageSource.getMessage(
        exception.messageKey(), exception.messageArguments(), LocaleContextHolder.getLocale());
  }

  private String message(String key) {
    return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
