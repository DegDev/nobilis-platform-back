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
package io.github.degdev.engine.common.settings;

import io.github.degdev.engine.common.crypto.CryptoService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write access to engine {@link Setting}s with transparent secret handling.
 *
 * <p>{@link #set} encrypts the value when {@code secret} is requested; {@link #get} decrypts it on
 * the way out. Callers always deal in plaintext — the ciphertext exists only in the column.
 *
 * <p>Lombok {@code @RequiredArgsConstructor} wires the {@code final} collaborators (Spring sees one
 * constructor and injects); {@code @Slf4j} provides the {@code log} field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

  private final SettingRepository repository;
  private final CryptoService cryptoService;

  /**
   * Reads a setting's value as plaintext, decrypting transparently when it is a secret so callers
   * never handle ciphertext.
   *
   * @param key the setting key
   * @return the plaintext value, or empty if the key is unset
   */
  @Transactional(readOnly = true)
  public Optional<String> get(String key) {
    return repository
        .findByKey(key)
        .map(
            setting ->
                setting.isSecret()
                    ? cryptoService.decrypt(setting.getValue())
                    : setting.getValue());
  }

  /**
   * Creates or updates a setting, encrypting the value first when {@code secret} so plaintext never
   * reaches the column. Callers always pass plaintext.
   *
   * @param key the setting key (created if absent, updated if present)
   * @param value the plaintext value to store
   * @param secret whether the value must be encrypted at rest
   * @return the persisted setting
   */
  @Transactional
  public Setting set(String key, String value, boolean secret) {
    String stored = secret ? cryptoService.encrypt(value) : value;
    Setting setting =
        repository
            .findByKey(key)
            .map(
                existing -> {
                  existing.setValue(stored);
                  existing.setSecret(secret);
                  return existing;
                })
            .orElseGet(() -> new Setting(key, stored, secret));
    log.debug("Saving setting '{}' (secret={})", key, secret);
    return repository.save(setting);
  }
}
