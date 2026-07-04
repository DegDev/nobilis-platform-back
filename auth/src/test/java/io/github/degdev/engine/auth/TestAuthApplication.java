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

import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * (auto-config packages, independent of {@code scanBasePackages}).
 *
 * <p>JPA auditing is now supplied by {@code common}'s {@code PersistenceAutoConfiguration} (mounted
 * from its {@code AutoConfiguration.imports} whenever a JPA {@code EntityManagerFactory} exists),
 * so it no longer needs an explicit {@code @Import}. Crypto stays absent: {@code
 * CryptoAutoConfiguration} is gated on {@code nobilis.crypto.master-key}, which these tests do not
 * set.
 */
@SpringBootApplication(scanBasePackages = "io.github.degdev.engine.auth.account")
public class TestAuthApplication {}
