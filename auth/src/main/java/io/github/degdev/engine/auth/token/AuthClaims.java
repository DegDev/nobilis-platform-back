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
 * subject (who the token is about), the granted roles, the realms and effective permissions the
 * token carries, and the issued/expiry instants.
 *
 * <p>{@code realms} and {@code permissions} are the "thick" part of the token: the coarse realm
 * gate and the atomic permissions a request may exercise, baked in at issue time so a gate can
 * authorize without a database round-trip. A token issued before these existed ("thin") still
 * validates — both come back as empty lists.
 *
 * @param subject the {@code sub} claim — typically the authenticated identity
 * @param roles the {@code roles} claim — role codes granted to the subject (display / back-compat)
 * @param realms the {@code realms} claim — {@link io.github.degdev.engine.auth.account.Realm} names
 * @param permissions the {@code permissions} claim — effective permission strings
 * @param issuedAt the {@code iat} claim as an {@link Instant}
 * @param expiresAt the {@code exp} claim as an {@link Instant}
 * @param loginAt the {@code loginAt} claim as an {@link Instant} — the original real-login instant,
 *     set once and carried forward unchanged across any silent token re-mint. A token issued before
 *     this claim existed falls back to {@code issuedAt}.
 */
public record AuthClaims(
    String subject,
    List<String> roles,
    List<String> realms,
    List<String> permissions,
    Instant issuedAt,
    Instant expiresAt,
    Instant loginAt) {}
