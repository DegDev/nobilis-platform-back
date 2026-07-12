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
package io.github.degdev.engine.ai.profile;

import io.github.degdev.engine.common.crypto.CryptoService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encrypt-on-write / decrypt-on-read access to {@link AiSecret} values, keyed by their composite
 * {@code ref} (conventionally {@code "<purpose>.<providerCode>.<fieldKey>"}). Mirrors {@code
 * SettingsService}'s secret discipline: {@link #read(String)} is the one path that ever produces
 * plaintext, and it exists for internal, call-time use (a future LLM client resolving an api-key
 * field) — never for a UI-facing read. A display/listing path (a later slice's admin screen) would
 * go through the raw {@link io.github.degdev.engine.ai.profile.AiSecretRepository} the same way
 * {@code SettingsService.find}/{@code list} return the raw entity without decrypting.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * AiServiceAutoConfiguration}, additionally gated on {@link CryptoService} being present (a
 * two-collaborator {@code @ConditionalOnBean}, mirroring {@code SettingsAutoConfiguration}) — a
 * host with no master key configured simply has no secret store, never a fail-fast boot. Lombok
 * {@code @RequiredArgsConstructor} wires the {@code final} collaborators.
 */
@RequiredArgsConstructor
public class AiSecretStore {

  private final AiSecretRepository repository;
  private final CryptoService cryptoService;

  /**
   * Encrypts and stores (or overwrites) a secret value.
   *
   * @param ref the composite {@code "<purpose>.<providerCode>.<fieldKey>"} key
   * @param plaintext the secret to protect
   */
  @Transactional
  public void store(String ref, String plaintext) {
    repository.save(new AiSecret(ref, cryptoService.encrypt(plaintext), Instant.now()));
  }

  /**
   * Reads a secret's plaintext for internal, call-time use (e.g. an LLM client resolving an api-key
   * field). Never for a UI-facing read — display paths use the raw repository and mask the
   * ciphertext instead.
   *
   * @param ref the composite {@code "<purpose>.<providerCode>.<fieldKey>"} key
   * @return the decrypted plaintext, or empty if no secret is stored under this ref
   */
  @Transactional(readOnly = true)
  public Optional<String> read(String ref) {
    return repository.findById(ref).map(secret -> cryptoService.decrypt(secret.getValue()));
  }
}
