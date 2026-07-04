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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.util.StringUtils;

/**
 * String-level secret encryption over {@link GcmCipher}.
 *
 * <p>Storage format is {@code "v1:" + Base64(IV ‖ ciphertext ‖ tag)} — a single self-describing
 * string that fits one column. The {@code v1:} prefix is a version tag: it lets a future
 * key/algorithm rotation introduce a {@code v2:} scheme while still recognising (and rejecting)
 * older payloads. Decryption is explicit and called at the use site, keeping plaintext secrets out
 * of entity memory except exactly when needed.
 *
 * <p>Instantiated by {@link CryptoAutoConfiguration} as a {@code @Bean} (not a component-scanned
 * {@code @Service}) so a host mounting {@code common} via auto-configuration gets it without
 * scanning {@code io.github.degdev.engine.common}.
 */
public class CryptoService {

  static final String VERSION_PREFIX = "v1:";
  private static final int AES_256_KEY_BYTES = 32;

  private final GcmCipher cipher;

  /**
   * Builds the service from the configured master key, failing fast on a missing or malformed key
   * so a misconfigured deployment never starts with broken crypto.
   *
   * @param properties holds the Base64-encoded 256-bit master key ({@code
   *     nobilis.crypto.master-key})
   * @throws IllegalStateException if the key is absent, not valid Base64, or not 256 bits
   */
  public CryptoService(CryptoProperties properties) {
    if (properties == null || !StringUtils.hasText(properties.masterKey())) {
      throw new IllegalStateException(
          "Missing required property 'nobilis.crypto.master-key'. Generate one with "
              + CryptoKeyGenerator.class.getName()
              + " and supply it via the environment (never commit it).");
    }
    byte[] key;
    try {
      key = Base64.getDecoder().decode(properties.masterKey().trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("'nobilis.crypto.master-key' is not valid Base64", e);
    }
    if (key.length != AES_256_KEY_BYTES) {
      throw new IllegalStateException(
          "'nobilis.crypto.master-key' must decode to "
              + AES_256_KEY_BYTES
              + " bytes (256 bits); got "
              + key.length);
    }
    this.cipher = new GcmCipher(key);
  }

  /**
   * Encrypts plaintext for storage in a single column. The {@code v1:} prefix tags the scheme so a
   * later key/algorithm rotation can introduce {@code v2:} without ambiguity.
   *
   * @param plaintext the secret to protect; must not be {@code null}
   * @return {@code "v1:" + Base64(IV ‖ ciphertext ‖ tag)}
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      throw new IllegalArgumentException("plaintext must not be null");
    }
    byte[] encrypted = cipher.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    return VERSION_PREFIX + Base64.getEncoder().encodeToString(encrypted);
  }

  /**
   * Decrypts a value produced by {@link #encrypt(String)}. The GCM tag is verified, so a tampered
   * or truncated payload fails loudly rather than returning corrupted plaintext.
   *
   * @param stored the {@code v1:}-prefixed ciphertext
   * @return the recovered plaintext
   * @throws CryptoException if the version prefix is missing/unknown, the payload is not valid
   *     Base64, or authentication fails
   */
  public String decrypt(String stored) {
    if (stored == null || !stored.startsWith(VERSION_PREFIX)) {
      throw new CryptoException(
          "unrecognised ciphertext: missing '" + VERSION_PREFIX + "' version prefix");
    }
    byte[] encrypted;
    try {
      encrypted = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
    } catch (IllegalArgumentException e) {
      throw new CryptoException("ciphertext payload is not valid Base64", e);
    }
    return new String(cipher.decrypt(encrypted), StandardCharsets.UTF_8);
  }
}
