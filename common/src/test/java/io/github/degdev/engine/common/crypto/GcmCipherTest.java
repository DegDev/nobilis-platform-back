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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GcmCipherTest {

  private static byte[] freshKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return key;
  }

  @Test
  void roundTripRecoversPlaintext() {
    GcmCipher cipher = new GcmCipher(freshKey());
    byte[] plaintext = "super-secret-token".getBytes(StandardCharsets.UTF_8);

    byte[] decrypted = cipher.decrypt(cipher.encrypt(plaintext));

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void freshIvMakesCiphertextNonDeterministic() {
    GcmCipher cipher = new GcmCipher(freshKey());
    byte[] plaintext = "same input".getBytes(StandardCharsets.UTF_8);

    byte[] first = cipher.encrypt(plaintext);
    byte[] second = cipher.encrypt(plaintext);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void tamperedCiphertextFailsAuthentication() {
    GcmCipher cipher = new GcmCipher(freshKey());
    byte[] encrypted = cipher.encrypt("authentic".getBytes(StandardCharsets.UTF_8));

    // Flip a bit in the last byte (inside the GCM tag) — decryption must fail, not return garbage.
    byte[] tampered = Arrays.copyOf(encrypted, encrypted.length);
    tampered[tampered.length - 1] ^= 0x01;

    assertThatThrownBy(() -> cipher.decrypt(tampered))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("authentication tag");
  }

  @Test
  void tamperedCiphertextBodyFailsAuthentication() {
    GcmCipher cipher = new GcmCipher(freshKey());
    byte[] encrypted = cipher.encrypt("authentic".getBytes(StandardCharsets.UTF_8));

    // Flip a bit just after the 12-byte IV (inside the ciphertext body).
    byte[] tampered = Arrays.copyOf(encrypted, encrypted.length);
    tampered[12] ^= 0x01;

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(CryptoException.class);
  }

  @Test
  void rejectsKeyOfWrongLength() {
    assertThatThrownBy(() -> new GcmCipher(new byte[16]))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("256 bits");
  }
}
