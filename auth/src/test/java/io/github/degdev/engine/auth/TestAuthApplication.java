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
package io.github.degdev.engine.auth;

import io.github.degdev.engine.common.persistence.JpaAuditingConfig;
import io.github.degdev.engine.common.persistence.SystemAuditorAware;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Test-only bootstrap. {@code auth} is a library jar with no runnable application of its own, so
 * the persistence integration slice needs a {@code @SpringBootConfiguration} to start a context
 * against.
 *
 * <p>The component scan is narrowed to the {@code account} feature on purpose: a blanket scan of
 * {@code io.github.degdev.engine.auth} would pick up the opt-in {@code AdminLoginController}
 * ({@code @RestController}) whose service only exists when admin-login is enabled — exactly the
 * blanket-scan the engine avoids in real hosts, which reach auth only through gated auto-config.
 * The account entities and repositories are still discovered from the auth base package
 * (auto-config packages, independent of {@code scanBasePackages}). The two common persistence beans
 * are imported explicitly (rather than widening the scan into {@code common}) so JPA auditing fills
 * the timestamps without dragging in crypto, which would demand a master key.
 */
@SpringBootApplication(scanBasePackages = "io.github.degdev.engine.auth.account")
@Import({JpaAuditingConfig.class, SystemAuditorAware.class})
public class TestAuthApplication {}
