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
package io.github.degdev.engine.common.notifications;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link NotificationTemplate}.
 *
 * <p>Lookup is by the owning type's database id + transport (the unique business-key pair). The
 * type's key is not on this entity (it's on {@link NotificationType}), so a key-based lookup goes
 * via {@link NotificationTypeRepository#findByKey} first, then {@link #findByTypeIdAndTransport} /
 * {@link #findByTypeId}.
 */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

  /**
   * Finds a template by its owning type id and transport.
   *
   * @param typeId the notification type's database id
   * @param transport the transport channel
   * @return the template, or empty if none exists for the pair
   */
  Optional<NotificationTemplate> findByTypeIdAndTransport(Long typeId, Transport transport);

  /**
   * Lists templates for one owning type, one page at a time.
   *
   * @param typeId the notification type's database id
   * @param pageable the page request
   * @return the requested page of templates for the type
   */
  Page<NotificationTemplate> findByTypeId(Long typeId, Pageable pageable);
}
