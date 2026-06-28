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

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Operator entrypoint that prints a fresh Base64-encoded 256-bit AES master key.
 *
 * <p>Run it standalone (it deliberately does not start a Spring context, so it cannot leak into
 * application startup) and place the printed value in the environment as {@code
 * nobilis.crypto.master-key}. The key is never written to a committed file.
 *
 * <pre>{@code
 * java -cp common.jar io.github.degdev.engine.common.crypto.CryptoKeyGenerator
 * }</pre>
 */
public final class CryptoKeyGenerator {

  private static final int AES_256_KEY_BYTES = 32;

  private CryptoKeyGenerator() {}

  /**
   * Generates a fresh, cryptographically strong 256-bit AES key. Exposed (and unit-tested)
   * separately from {@link #main(String[])} so the generation can be exercised without stdout.
   *
   * @return the key Base64-encoded, ready to drop into {@code nobilis.crypto.master-key}
   */
  public static String generateBase64Key() {
    byte[] key = new byte[AES_256_KEY_BYTES];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  /**
   * Prints a fresh Base64 master key to stdout for the operator to capture into the environment.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    System.out.println(generateBase64Key());
  }
}
