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
package io.github.degdev.engine.auth.token;

import java.time.Instant;
import java.util.List;

/**
 * The verified payload of a JWT, as returned by {@link JwtService#validate(String)}. Holds the
 * subject (who the token is about), the granted roles, and the issued/expiry instants.
 *
 * @param subject the {@code sub} claim — typically the authenticated identity
 * @param roles the {@code roles} claim — authorities granted to the subject
 * @param issuedAt the {@code iat} claim as an {@link Instant}
 * @param expiresAt the {@code exp} claim as an {@link Instant}
 */
public record AuthClaims(String subject, List<String> roles, Instant issuedAt, Instant expiresAt) {}
