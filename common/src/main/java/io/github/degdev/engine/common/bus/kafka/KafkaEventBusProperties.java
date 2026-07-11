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
package io.github.degdev.engine.common.bus.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Kafka adapter configuration. The runnable that opts into {@code nobilis.integration.bus=kafka}
 * supplies these values — mirrors the runnable-owns-config model used by {@code CryptoProperties}.
 */
@ConfigurationProperties(prefix = "nobilis.integration.bus.kafka")
public record KafkaEventBusProperties(
    String bootstrapServers,
    @DefaultValue("nobilis-integration-worker") String groupId,
    @DefaultValue("2") int retryAttempts,
    @DefaultValue("1000") long retryBackoffMs) {}
