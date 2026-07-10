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
package io.github.degdev.engine.app.content;

import io.github.degdev.engine.common.cms.ContentBlockAutoConfiguration;
import io.github.degdev.engine.common.cms.ContentBlockService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Mounts {@link PortalContentController} only when its store exists — the same shape as {@code
 * ContentBlockWebAutoConfiguration} in {@code admin}. The controller is a request handler
 * deliberately NOT component-scanned (see {@link io.github.degdev.engine.app.AppApplication}'s
 * exclude filter); this auto-configuration registers it as a {@code @Bean} gated on a {@link
 * ContentBlockService} being present, which happens exactly when the portal's datasource profile is
 * active (see {@link ContentBlockAutoConfiguration}). The stateless default host mounts no content
 * endpoint.
 *
 * <p>{@link ConditionalOnBean} is reliable here because this is an auto-configuration, evaluated
 * after regular configuration and (via {@code after}) after common's {@link
 * ContentBlockAutoConfiguration} has had its chance to contribute the service. Registered from
 * app's {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration(after = ContentBlockAutoConfiguration.class)
@ConditionalOnBean(ContentBlockService.class)
public class PortalContentWebAutoConfiguration {

  /**
   * Registers the public content-read controller when the store is present.
   *
   * @param contentBlockService the CMS read service
   * @return the portal content controller
   */
  @Bean
  public PortalContentController portalContentController(ContentBlockService contentBlockService) {
    return new PortalContentController(contentBlockService);
  }
}
