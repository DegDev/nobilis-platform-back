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
package io.github.degdev.engine.ai.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

/**
 * Registers {@code ai}'s persistent artifacts so a host gets the AI-provider/profile entities and
 * their Spring Data repositories by depending on {@code ai} — not by component-scanning {@code
 * io.github.degdev.engine.ai} (which a real host's scan root never reaches). This is {@code ai}'s
 * analog of {@code auth}'s {@code AuthPersistenceAutoConfiguration}.
 *
 * <p>{@link AutoConfigurationPackage}, unconditional, for the same reasons as {@code
 * AuthPersistenceAutoConfiguration}: it runs during configuration parsing, before any {@code
 * EntityManagerFactory} exists, so the package is registered before Spring Data's repository scan
 * reads it; gating it on the EMF bean would defer registration too late. On a stateless host with
 * no EMF, nothing scans the package, so no repository beans are created and the host boots clean.
 *
 * <p>No {@code @Bean} is contributed here — {@code AiProfileService}/{@code AiProviderDefaults}/
 * {@code AiSecretStore} arrive in the next slice, EMF-gated per the pattern in {@code common}'s
 * {@code SettingsAutoConfiguration}. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "io.github.degdev.engine.ai")
public class AiPersistenceAutoConfiguration {}
