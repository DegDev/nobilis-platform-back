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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission a request must carry to invoke a controller handler. Read at request time
 * by {@link RequiresPermissionInterceptor}, which rejects a caller whose token lacks the permission
 * with {@code 403}.
 *
 * <p>This is the MVC-layer authorization check and is deliberately narrow: it tests only the atomic
 * permission (e.g. {@code EnginePermissions.SETTINGS_MANAGE}). The coarse realm/anonymous gate runs
 * earlier, at the servlet layer (the host's contour filter over the auth gate); this annotation
 * does not repeat it. May be placed on a method or, as a default for every handler, on the
 * controller type.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

  /**
   * {@return the permission string the request's token must contain} Typically an {@code
   * EnginePermissions} constant.
   */
  String value();
}
