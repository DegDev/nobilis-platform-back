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
package io.github.degdev.engine.common.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Mounts {@link CryptoService}, opt-in on a configured master key ({@code
 * nobilis.crypto.master-key}). A host that supplies no key simply has no crypto — never a fail-fast
 * boot — which is why the whole configuration is gated rather than letting {@link CryptoService}'s
 * key validation crash a crypto-less host. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}: a host opts in by depending on {@code common} and
 * supplying the key, not by component-scanning {@code io.github.degdev.engine.common} (which its
 * scan root never reaches).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nobilis.crypto", name = "master-key")
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

  /**
   * Provides the secret-encryption service when a master key is configured.
   *
   * @param properties the Base64-encoded 256-bit master key holder
   * @return the crypto service
   */
  @Bean
  public CryptoService cryptoService(CryptoProperties properties) {
    return new CryptoService(properties);
  }
}
