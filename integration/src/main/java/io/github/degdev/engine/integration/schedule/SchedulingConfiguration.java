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
package io.github.degdev.engine.integration.schedule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's {@code @Scheduled} mechanism for the worker, opt-in on {@code
 * nobilis.integration.scheduling.enabled}. Off by default — a host that never sets this flag has no
 * scheduling at all, same boot-time-gate shape as {@code CryptoAutoConfiguration} and the
 * notification transports (never an in-method runtime check). A plain {@code @Configuration}, not
 * {@code @AutoConfiguration}: {@code integration} is the worker app itself, not a library other
 * hosts depend on, so it is picked up directly by {@code IntegrationApplication}'s own component
 * scan rather than a {@code META-INF/spring/...AutoConfiguration.imports} entry.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "nobilis.integration.scheduling", name = "enabled")
class SchedulingConfiguration {}
