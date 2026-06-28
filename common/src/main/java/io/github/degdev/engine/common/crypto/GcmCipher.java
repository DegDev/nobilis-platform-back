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

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM authenticated-encryption primitive.
 *
 * <p>Output layout is {@code IV(12) ‖ ciphertext ‖ tag(16)} as a single byte array: a fresh 96-bit
 * IV is generated per call with {@link SecureRandom} (NIST SP 800-38D: never reuse an IV under the
 * same key) and prepended to the ciphertext; the 128-bit GCM authentication tag is appended by the
 * JCA provider. {@link #decrypt} verifies that tag and throws on any mismatch, so tampered input
 * fails loudly instead of returning corrupted plaintext.
 */
final class GcmCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int AES_256_KEY_BYTES = 32;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public GcmCipher(byte[] keyBytes) {
    if (keyBytes == null || keyBytes.length != AES_256_KEY_BYTES) {
      throw new CryptoException(
          "AES-256 key must be exactly " + AES_256_KEY_BYTES + " bytes (256 bits)");
    }
    this.key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
  }

  public byte[] encrypt(byte[] plaintext) {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext);
      return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    } catch (GeneralSecurityException e) {
      throw new CryptoException("AES-GCM encryption failed", e);
    }
  }

  public byte[] decrypt(byte[] ivAndCiphertext) {
    if (ivAndCiphertext == null || ivAndCiphertext.length <= IV_LENGTH_BYTES) {
      throw new CryptoException(
          "ciphertext is too short to contain an IV and an authentication tag");
    }
    ByteBuffer buffer = ByteBuffer.wrap(ivAndCiphertext);
    byte[] iv = new byte[IV_LENGTH_BYTES];
    buffer.get(iv);
    byte[] ciphertext = new byte[buffer.remaining()];
    buffer.get(ciphertext);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("AES-GCM decryption failed: authentication tag did not verify", e);
    }
  }
}
