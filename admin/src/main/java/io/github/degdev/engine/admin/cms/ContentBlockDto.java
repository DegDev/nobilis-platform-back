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

import io.github.degdev.engine.common.cms.ContentBlock;
import io.github.degdev.engine.common.cms.ContentStatus;
import io.github.degdev.engine.common.cms.ContentTranslation;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The API view of a {@link ContentBlock}: its key, publication status, and translations keyed by
 * locale.
 *
 * @param key the unique, immutable natural key
 * @param status the publication status
 * @param translations each translated body, keyed by locale code
 */
public record ContentBlockDto(String key, ContentStatus status, Map<String, String> translations) {

  /**
   * Projects an entity to its API view.
   *
   * @param block the stored content block (with its translations loaded)
   * @return the API view
   */
  public static ContentBlockDto from(ContentBlock block) {
    return new ContentBlockDto(
        block.getKey(),
        block.getStatus(),
        block.getTranslations().stream()
            .collect(Collectors.toMap(ContentTranslation::getLocale, ContentTranslation::getBody)));
  }
}
