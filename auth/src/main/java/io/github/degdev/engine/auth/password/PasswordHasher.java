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
package io.github.degdev.engine.auth.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * One-way password hashing with BCrypt (per-hash random salt, adaptive work factor). Delegates to
 * Spring Security's {@link BCryptPasswordEncoder} — the vetted implementation — rather than
 * hand-rolling a KDF; only this thin, dependency-light façade is exposed to the engine.
 * Verification is via {@link PasswordEncoder#matches} (constant-time within BCrypt), never by
 * re-hashing and comparing strings.
 */
public final class PasswordHasher {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  /**
   * Hashes a raw password for storage.
   *
   * @param rawPassword the plaintext password; must not be {@code null}
   * @return a self-describing BCrypt hash (algorithm, cost, salt, and digest in one string)
   */
  public String hash(String rawPassword) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword must not be null");
    }
    return encoder.encode(rawPassword);
  }

  /**
   * Verifies a raw password against a stored BCrypt hash.
   *
   * @param rawPassword the candidate plaintext
   * @param storedHash a hash previously produced by {@link #hash(String)}
   * @return {@code true} iff the password matches; {@code false} on any null input or mismatch
   */
  public boolean matches(String rawPassword, String storedHash) {
    if (rawPassword == null || storedHash == null) {
      return false;
    }
    return encoder.matches(rawPassword, storedHash);
  }
}
