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

import io.github.degdev.engine.common.cms.ContentBlockService;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The portal's public, anonymous read path onto CMS content: {@code
 * ContentBlockService.readPublished} exposed over HTTP, unchanged and un-wrapped. The portal host
 * has no security contour at all (no {@code AdminContourFilter}, no security starter dependency),
 * so this endpoint is anonymous by construction — nothing to bypass, nothing to configure.
 *
 * <p>Never returns a 5xx for a missing or unpublished block: {@link
 * ContentBlockService#readPublished} "never errors" by contract, returning {@link Optional#empty()}
 * for an absent key, a DRAFT block, or an unsupported/blank locale (which it silently resolves via
 * its {@code ru} fallback instead of rejecting). This controller maps that emptiness to a plain
 * {@code 404}, and a present body to a plain-text {@code 200} — deliberately not the admin {@code
 * ContentBlockDto} shape, which carries draft/publish workflow fields this public path has no use
 * for.
 *
 * <p><b>Mounted only with a store.</b> Same shape as {@code ContentBlockWebAutoConfiguration}: this
 * is a {@code @RestController} excluded from {@link io.github.degdev.engine.app.AppApplication}'s
 * component scan and registered as a {@code @Bean} by {@link PortalContentWebAutoConfiguration},
 * gated on a {@link ContentBlockService} existing. The stateless default host mounts no content
 * endpoint at all.
 */
@RestController
@RequestMapping("/api/content")
public class PortalContentController {

  private final ContentBlockService contentBlockService;

  /**
   * Creates the controller.
   *
   * @param contentBlockService the CMS read service
   */
  public PortalContentController(ContentBlockService contentBlockService) {
    this.contentBlockService = contentBlockService;
  }

  /**
   * Resolves a published content block's body for the requesting locale.
   *
   * @param key the content block key
   * @param locale the requested locale code (e.g. {@code ru}, {@code ro}); may be
   *     blank/null/unknown — {@link ContentBlockService#readPublished} falls back to {@code ru}
   *     rather than rejecting
   * @return {@code 200} with the plain-text body if a published translation resolves, else {@code
   *     404} (never a {@code 5xx})
   */
  @GetMapping("/{key}")
  public ResponseEntity<String> read(
      @PathVariable String key, @RequestParam(required = false) String locale) {
    Optional<String> body = contentBlockService.readPublished(key, locale);
    return body.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
