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
package io.github.degdev.engine.integration.dispatch;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * {@link NotificationTransport} adapter for {@code Transport.EMAIL}, sent via {@link
 * JavaMailSender} (Spring Boot's {@code spring-boot-starter-mail} auto-configuration, pointed at
 * Mailpit in dev via {@code spring.mail.host}). The only transport adapter this slice, so a plain
 * {@code @Service} singleton is sufficient — port-selection between multiple transports only
 * becomes relevant once Telegram/SMS adapters exist.
 */
@Service
@RequiredArgsConstructor
class EmailNotificationTransport implements NotificationTransport {

  private final JavaMailSender mailSender;

  @Override
  public void send(String recipient, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }
}
