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
package io.github.degdev.engine.auth.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

/**
 * Registers {@code auth}'s persistent artifacts so a host gets the account/role entities and their
 * Spring Data repositories by depending on {@code auth} — not by component-scanning {@code
 * io.github.degdev.engine.auth} (which a real host's scan root never reaches). This is auth's
 * analog of {@code common}'s {@code SettingsAutoConfiguration}.
 *
 * <p><b>Why {@link AutoConfigurationPackage} and not
 * {@code @EntityScan}/{@code @EnableJpaRepositories}.</b> Those would have to be processed before
 * the {@code EntityManagerFactory} is built and cannot be gated on it; worse, an unconditional
 * {@code @EntityScan} in a library REPLACES a host's entity packages and
 * {@code @EnableJpaRepositories} makes Boot's repository auto-configuration back off GLOBALLY —
 * both break a sibling module. {@link AutoConfigurationPackage} instead ADDS a package to the
 * auto-configuration packages, which Boot's entity scan AND Spring Data's repository scan both
 * consume — additive, no replacement, no back-off.
 *
 * <p><b>Why the registration is UNCONDITIONAL</b> (no
 * {@code @ConditionalOnBean(EntityManagerFactory)}). {@link AutoConfigurationPackage} runs during
 * configuration parsing, before any {@code EntityManagerFactory} exists, so the package is already
 * registered when Spring Data's repository scan reads it. Gating it on the EMF bean would defer the
 * registration until after the EMF is built — potentially after the repository scan has already run
 * — and the repositories would never become beans even on a JPA host. It needs no gate to stay off
 * a stateless host either: with no EMF (the milestone-03 admin host excludes the DataSource/JPA
 * auto-configuration) nothing scans the package, so no repository beans are created and the host
 * boots clean — exactly how {@code common}'s always-registered {@code engine.common} package is
 * harmless there.
 *
 * <p><b>Why the module root {@code io.github.degdev.engine.auth}.</b> All of auth's persistent
 * artifacts (the {@code account} and {@code role} features) live under it, so one add covers them.
 * {@code AutoConfigurationPackages} holds base packages in a set, so a host that already roots at
 * {@code engine.auth} (auth's own test bootstrap) collapses to a single scan rather than
 * double-scanning a repository. No {@code @Bean} is contributed here — the account/role services
 * arrive in a later pass. Registered from {@code META-INF/spring/…AutoConfiguration.imports}.
 */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "io.github.degdev.engine.auth")
public class AuthPersistenceAutoConfiguration {}
