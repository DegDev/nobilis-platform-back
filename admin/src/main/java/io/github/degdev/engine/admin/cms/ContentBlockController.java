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
package io.github.degdev.engine.admin.cms;

import io.github.degdev.engine.admin.api.RequiresPermission;
import io.github.degdev.engine.auth.role.EnginePermissions;
import io.github.degdev.engine.common.cms.ContentBlockNotFoundException;
import io.github.degdev.engine.common.cms.ContentBlockService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content blocks CRUD — the admin management surface on top of {@link ContentBlockService},
 * following the {@code SettingsController} shape: RFC 9457 errors ({@code GlobalExceptionHandler}),
 * {@code Pageable}/{@code PagedModel} pagination, and {@link RequiresPermission} at the class level
 * so every handler needs {@link EnginePermissions#CONTENT_MANAGE}.
 *
 * <p>Unlike settings/roles, {@code ContentBlockService} already throws its own typed domain
 * exceptions ({@code ContentBlockNotFoundException}, {@code ContentBlockConflictException}, {@code
 * UnsupportedLocaleException}) for every failure path, so this controller lets them propagate as-is
 * instead of wrapping them in the admin package's generic {@code NotFoundException} — {@code get}
 * throws the same {@code ContentBlockNotFoundException} the write paths do, for one 404 shape per
 * domain. Publication is admin-only ({@code updateStatus}); the public read path ({@code
 * readPublished}) is a separate, unauthenticated surface built elsewhere.
 *
 * <p><b>Mounted only with a store.</b> Like {@code SettingsController} this is a
 * {@code @RestController} EXCLUDED from the host component scan (see {@code AdminApplication}) and
 * registered as a {@code @Bean} by {@code ContentBlockWebAutoConfiguration}, gated on a {@link
 * ContentBlockService} existing — i.e. only when the database is active. The stateless default host
 * mounts no content-block endpoints.
 */
@RestController
@RequestMapping("/admin/api/content-blocks")
@RequiresPermission(EnginePermissions.CONTENT_MANAGE)
public class ContentBlockController {

  private final ContentBlockService contentBlockService;

  /**
   * Creates the controller.
   *
   * @param contentBlockService the content block store
   */
  public ContentBlockController(ContentBlockService contentBlockService) {
    this.contentBlockService = contentBlockService;
  }

  /**
   * Lists content blocks (any status), one page at a time.
   *
   * @param pageable the page request ({@code ?page=&size=&sort=})
   * @return a page of content blocks as {@link ContentBlockDto}s
   */
  @GetMapping
  public PagedModel<ContentBlockDto> list(Pageable pageable) {
    return new PagedModel<>(contentBlockService.list(pageable).map(ContentBlockDto::from));
  }

  /**
   * Reads one content block by key (any status).
   *
   * @param key the content block key
   * @return the block as a {@link ContentBlockDto}
   * @throws ContentBlockNotFoundException if no block exists for {@code key}
   */
  @GetMapping("/{key}")
  public ContentBlockDto get(@PathVariable String key) {
    return contentBlockService
        .find(key)
        .map(ContentBlockDto::from)
        .orElseThrow(
            () -> new ContentBlockNotFoundException("Content block '" + key + "' does not exist"));
  }

  /**
   * Creates a new, translation-less content block.
   *
   * @param request the validated create request
   * @return {@code 201} with the created block
   */
  @PostMapping
  public ResponseEntity<ContentBlockDto> create(
      @Valid @RequestBody ContentBlockCreateRequest request) {
    ContentBlockDto created =
        ContentBlockDto.from(contentBlockService.create(request.key(), request.status()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Updates a block's publication status.
   *
   * @param key the content block key
   * @param request the validated status update request
   * @return the updated block
   */
  @PutMapping("/{key}/status")
  public ContentBlockDto updateStatus(
      @PathVariable String key, @Valid @RequestBody ContentBlockStatusUpdateRequest request) {
    return ContentBlockDto.from(contentBlockService.updateStatus(key, request.status()));
  }

  /**
   * Creates or replaces a block's translation for one locale.
   *
   * @param key the content block key
   * @param locale the locale code (e.g. {@code ru}, {@code ro})
   * @param request the validated translation request
   * @return the updated block
   */
  @PutMapping("/{key}/translations/{locale}")
  public ContentBlockDto upsertTranslation(
      @PathVariable String key,
      @PathVariable String locale,
      @Valid @RequestBody ContentBlockTranslationRequest request) {
    return ContentBlockDto.from(contentBlockService.upsertTranslation(key, locale, request.body()));
  }

  /**
   * Removes a block's translation for one locale.
   *
   * @param key the content block key
   * @param locale the locale code to remove
   * @return {@code 204} when removed
   */
  @DeleteMapping("/{key}/translations/{locale}")
  public ResponseEntity<Void> removeTranslation(
      @PathVariable String key, @PathVariable String locale) {
    contentBlockService.removeTranslation(key, locale);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deletes a content block and its translations.
   *
   * @param key the content block key
   * @return {@code 204} when deleted
   * @throws ContentBlockNotFoundException if no block exists for {@code key}
   */
  @DeleteMapping("/{key}")
  public ResponseEntity<Void> delete(@PathVariable String key) {
    if (!contentBlockService.delete(key)) {
      throw new ContentBlockNotFoundException("Content block '" + key + "' does not exist");
    }
    return ResponseEntity.noContent().build();
  }
}
