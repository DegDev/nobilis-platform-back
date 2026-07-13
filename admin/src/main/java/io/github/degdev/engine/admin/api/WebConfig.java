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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring for the admin REST framework. Registers {@link RequiresPermissionInterceptor} over the
 * admin API paths so every handler's {@link RequiresPermission} declaration is enforced. The login
 * endpoint and static/error paths are not API handlers and must not be gated here (they are handled
 * by the servlet-layer contour).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String apiUrl;

  public WebConfig(@Value("${nobilis.api.v1.url:/api}") String apiUrl) {
    this.apiUrl = apiUrl;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new RequiresPermissionInterceptor())
        .addPathPatterns(apiUrl + "/admin/**");
  }
}
