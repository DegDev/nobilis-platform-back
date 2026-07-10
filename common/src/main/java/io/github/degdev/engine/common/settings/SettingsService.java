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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read/write access to engine {@link Setting}s with transparent secret handling.
 *
 * <p>{@link #set} encrypts the value when {@code secret} is requested; {@link #get} decrypts it on
 * the way out. Callers that read a secret <em>for use</em> go through {@link #get} and always deal
 * in plaintext — the ciphertext exists only in the column. Callers that merely list or display
 * settings (the admin API) go through {@link #find} / {@link #list}, which return the raw {@link
 * Setting} entity and never decrypt, so a secret's plaintext is never produced for a display path.
 *
 * <p>Not a {@code @Service}: it is wired as an explicit {@code @Bean} by {@code
 * SettingsAutoConfiguration}, gated on both a JPA {@code EntityManagerFactory} and a {@link
 * CryptoService} being present, so a stateless or crypto-less host simply has no settings service
 * rather than a fail-fast boot. Lombok {@code @RequiredArgsConstructor} wires the {@code final}
 * collaborators; {@code @Slf4j} provides the {@code log} field.
 */
@Slf4j
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
   * Reads a setting as its raw {@link Setting} entity, WITHOUT decrypting. Display/administration
   * paths use this (not {@link #get}) so a secret's plaintext is never produced merely to list or
   * inspect it — the caller masks the stored value.
   *
   * @param key the setting key
   * @return the setting entity, or empty if the key is unset
   */
  @Transactional(readOnly = true)
  public Optional<Setting> find(String key) {
    return repository.findByKey(key);
  }

  /**
   * Lists settings as raw {@link Setting} entities, a page at a time and WITHOUT decrypting (same
   * no-plaintext contract as {@link #find}). The caller masks secret values.
   *
   * @param keyPrefix when non-blank, only keys starting with this prefix are returned; blank or
   *     {@code null} lists everything
   * @param pageable the page request (page number, size, sort)
   * @return the requested page of settings
   */
  @Transactional(readOnly = true)
  public Page<Setting> list(String keyPrefix, Pageable pageable) {
    return StringUtils.hasText(keyPrefix)
        ? repository.findByKeyStartingWith(keyPrefix, pageable)
        : repository.findAll(pageable);
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

  /**
   * Deletes a setting by key, reporting whether one existed.
   *
   * @param key the setting key
   * @return {@code true} if a setting was deleted, {@code false} if the key was already unset
   */
  @Transactional
  public boolean delete(String key) {
    return repository
        .findByKey(key)
        .map(
            setting -> {
              repository.delete(setting);
              log.debug("Deleted setting '{}'", key);
              return true;
            })
        .orElse(false);
  }
}
