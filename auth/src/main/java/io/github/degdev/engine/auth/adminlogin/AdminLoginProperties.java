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
package io.github.degdev.engine.auth.adminlogin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the admin email/password login strategy. The whole feature is off until {@code
 * enabled} is set true (opt-in by default). The single admin credential is supplied via the
 * environment: {@code email} and {@code passwordHash} (a BCrypt hash, NEVER a plaintext password
 * and NEVER committed).
 *
 * @param enabled mounts the login endpoint when {@code true}; defaults to {@code false}
 * @param email the admin's email (the login identity)
 * @param passwordHash the admin's BCrypt password hash; env-supplied, never committed
 */
@ConfigurationProperties(prefix = "nobilis.auth.admin-login")
public record AdminLoginProperties(
    @DefaultValue("false") boolean enabled, String email, String passwordHash) {}
