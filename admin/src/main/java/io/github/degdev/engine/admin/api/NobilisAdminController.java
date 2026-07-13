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
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composed class-level annotation for an admin REST controller. Folds {@link RestController} and
 * {@link RequiresPermission} (via {@link #permission()}) into one declaration — the two class-level
 * annotations that were previously repeated verbatim across every admin controller.
 *
 * <p><b>Does NOT fold {@code @RequestMapping}.</b> An {@code @AliasFor} attribute override can only
 * alias one value onto another attribute — it has no concatenation/templating mechanism, so a
 * {@code segment} attribute here could not be combined with a base-path property into one path at
 * annotation-merge time. Every admin controller therefore keeps its own {@code @RequestMapping},
 * using the {@code nobilis.api.v1.url} property placeholder for the base:
 *
 * <pre>{@code
 * @NobilisAdminController(permission = EnginePermissions.ACCOUNT_MANAGE)
 * @RequestMapping("${nobilis.api.v1.url:/api}/admin/accounts")
 * public class AccountController { ... }
 * }</pre>
 *
 * <p><b>Mount caveat.</b> This annotation changes how a controller is DECLARED, not its type — the
 * {@code ASSIGNABLE_TYPE} component-scan exclude filter in {@code AdminApplication} still matches
 * the controller class directly, so the exclude list is untouched by adopting this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@RestController
@RequiresPermission("")
public @interface NobilisAdminController {

  /**
   * {@return the permission a request must carry to invoke any handler on this controller} Aliased
   * onto {@link RequiresPermission#value()}.
   */
  @AliasFor(annotation = RequiresPermission.class, attribute = "value")
  String permission();
}
