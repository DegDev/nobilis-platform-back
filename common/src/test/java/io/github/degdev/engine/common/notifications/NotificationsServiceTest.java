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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.degdev.engine.common.i18n.LocaleResolver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit-level: mocked repositories with a real {@link LocaleResolver} prove {@link
 * NotificationsService#resolveForDispatch}'s locale exact-match, {@code en}-fallback,
 * disabled-type-exclusion and no-match behavior — the milestone-04 dispatch read path.
 */
class NotificationsServiceTest {

  private NotificationTypeRepository typeRepository;
  private NotificationTemplateRepository templateRepository;
  private NotificationsService service;

  @BeforeEach
  void setUp() {
    typeRepository = Mockito.mock(NotificationTypeRepository.class);
    templateRepository = Mockito.mock(NotificationTemplateRepository.class);
    service = new NotificationsService(typeRepository, templateRepository, new LocaleResolver());
  }

  @Test
  void resolveForDispatchReturnsTheExactLocale() {
    NotificationType type = new NotificationType("order.created", true, null);
    NotificationTemplate template = new NotificationTemplate(type, Transport.EMAIL);
    template.addTranslation(new NotificationTemplateTranslation("ru", "Заказ создан", "Тело"));
    template.addTranslation(new NotificationTemplateTranslation("ro", "Comandă creată", "Corp"));
    when(typeRepository.findByKey("order.created")).thenReturn(Optional.of(type));
    when(templateRepository.findByTypeIdAndTransport(any(), any()))
        .thenReturn(Optional.of(template));

    Optional<NotificationTemplateTranslation> resolved =
        service.resolveForDispatch("order.created", Transport.EMAIL, "ro");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getBody()).isEqualTo("Corp");
  }

  @Test
  void resolveForDispatchFallsBackToEnWhenTheRequestedLocaleIsMissing() {
    NotificationType type = new NotificationType("order.created", true, null);
    NotificationTemplate template = new NotificationTemplate(type, Transport.EMAIL);
    template.addTranslation(new NotificationTemplateTranslation("en", "Order created", "Body"));
    when(typeRepository.findByKey("order.created")).thenReturn(Optional.of(type));
    when(templateRepository.findByTypeIdAndTransport(any(), any()))
        .thenReturn(Optional.of(template));

    Optional<NotificationTemplateTranslation> resolved =
        service.resolveForDispatch("order.created", Transport.EMAIL, "ro");

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getBody()).isEqualTo("Body");
  }

  @Test
  void resolveForDispatchExcludesADisabledType() {
    NotificationType type = new NotificationType("order.created", false, null);
    when(typeRepository.findByKey("order.created")).thenReturn(Optional.of(type));

    Optional<NotificationTemplateTranslation> resolved =
        service.resolveForDispatch("order.created", Transport.EMAIL, "ru");

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolveForDispatchReturnsEmptyForAnUnknownType() {
    when(typeRepository.findByKey("absent")).thenReturn(Optional.empty());

    Optional<NotificationTemplateTranslation> resolved =
        service.resolveForDispatch("absent", Transport.EMAIL, "ru");

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolveForDispatchReturnsEmptyWhenNoTemplateExistsForTheTransport() {
    NotificationType type = new NotificationType("order.created", true, null);
    when(typeRepository.findByKey("order.created")).thenReturn(Optional.of(type));
    when(templateRepository.findByTypeIdAndTransport(any(), any())).thenReturn(Optional.empty());

    Optional<NotificationTemplateTranslation> resolved =
        service.resolveForDispatch("order.created", Transport.TELEGRAM, "ru");

    assertThat(resolved).isEmpty();
  }
}
