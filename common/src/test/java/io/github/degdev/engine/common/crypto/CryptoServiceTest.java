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

import org.junit.jupiter.api.Test;

class CryptoServiceTest {

  private static CryptoService service() {
    return new CryptoService(new CryptoProperties(CryptoKeyGenerator.generateBase64Key()));
  }

  @Test
  void roundTripRecoversPlaintext() {
    CryptoService service = service();
    String plaintext = "bank-api-key-Ω-Ω";

    String decrypted = service.decrypt(service.encrypt(plaintext));

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void ciphertextCarriesVersionPrefix() {
    assertThat(service().encrypt("anything")).startsWith("v1:");
  }

  @Test
  void decryptRejectsMissingVersionPrefix() {
    assertThatThrownBy(() -> service().decrypt("not-a-versioned-payload"))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("version prefix");
  }

  @Test
  void decryptRejectsTamperedPayload() {
    CryptoService service = service();
    String encrypted = service.encrypt("authentic");

    // Mutate a character inside the Base64 payload (after the "v1:" prefix).
    char[] chars = encrypted.toCharArray();
    int idx = encrypted.length() - 2;
    chars[idx] = chars[idx] == 'A' ? 'B' : 'A';

    assertThatThrownBy(() -> service.decrypt(new String(chars)))
        .isInstanceOf(CryptoException.class);
  }

  @Test
  void rejectsMissingMasterKey() {
    assertThatThrownBy(() -> new CryptoService(new CryptoProperties("  ")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nobilis.crypto.master-key");
  }

  @Test
  void rejectsMasterKeyOfWrongLength() {
    // 16 bytes Base64-encoded — valid Base64 but not AES-256.
    String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> new CryptoService(new CryptoProperties(shortKey)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256 bits");
  }
}
