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

import io.github.degdev.engine.auth.gate.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RequiresPermission} at the MVC layer. For a handler that declares a required
 * permission (on the method, else on the controller type), it reads the current request's claims
 * via {@link AuthContextHolder} and throws {@link ForbiddenException} when the permission is absent
 * — the exception is turned into an RFC 9457 {@code 403} by {@link GlobalExceptionHandler}, keeping
 * the error contract uniform. Throwing (rather than writing the response here) is deliberate: a
 * hand-written status would bypass the problem+json advice.
 *
 * <p>This runs AFTER the servlet-layer contour filter, which has already rejected anonymous and
 * wrong-realm callers ({@code 401}/{@code 403}). So the interceptor tests only the atomic
 * permission and never re-checks the realm — no duplicated policy. A handler without {@link
 * RequiresPermission} passes through untouched.
 *
 * <p>Lookup uses {@link AnnotatedElementUtils#findMergedAnnotation}, not {@code
 * Class#getAnnotation}: a controller may carry {@link RequiresPermission} indirectly, folded in
 * through the {@code @NobilisAdminController} composed annotation via {@code @AliasFor} — plain
 * reflection sees only directly-present annotations and would miss it.
 */
public class RequiresPermissionInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RequiresPermission required =
        AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.getMethod(), RequiresPermission.class);
    if (required == null) {
      required =
          AnnotatedElementUtils.findMergedAnnotation(
              handlerMethod.getBeanType(), RequiresPermission.class);
    }
    if (required != null && !AuthContextHolder.hasPermission(required.value())) {
      throw new ForbiddenException();
    }
    return true;
  }
}
