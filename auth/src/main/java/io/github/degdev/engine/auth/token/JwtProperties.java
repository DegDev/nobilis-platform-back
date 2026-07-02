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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT signing configuration. The {@code secret} is a Base64-encoded key of at least 256 bits,
 * supplied via the environment (property {@code nobilis.auth.jwt.secret}) and NEVER committed — it
 * has the same handling contract as the crypto master key. When the secret is absent no {@link
 * JwtService} bean is created, so the engine still starts with auth on the classpath but unused.
 *
 * @param secret Base64-encoded HMAC-SHA256 key (≥ 256 bits); env-supplied, never committed
 * @param ttl how long an issued token stays valid; ISO-8601 duration, defaults to 30 minutes
 */
@ConfigurationProperties(prefix = "nobilis.auth.jwt")
public record JwtProperties(String secret, @DefaultValue("PT30M") Duration ttl) {}
