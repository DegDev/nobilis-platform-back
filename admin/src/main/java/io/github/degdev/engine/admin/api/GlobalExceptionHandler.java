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

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = ex.getBody();
    body.setDetail("Validation failed");
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
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
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  /**
   * Maps a missing permission to {@code 403}.
   *
   * @param ex the forbidden signal
   * @return a {@code 403} problem detail with a non-revealing message
   */
  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
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
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}
