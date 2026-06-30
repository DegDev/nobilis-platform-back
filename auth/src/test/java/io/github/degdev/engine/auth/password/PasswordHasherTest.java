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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

  // A throwaway plaintext used only as hashing input within this test; not a stored credential.
  private static final String RAW = "correct-horse-battery-staple";

  private final PasswordHasher hasher = new PasswordHasher();

  @Test
  void hashIsNotThePlaintext() {
    String hash = hasher.hash(RAW);

    assertThat(hash).isNotEqualTo(RAW).startsWith("$2");
  }

  @Test
  void verifyAcceptsTheCorrectPassword() {
    assertThat(hasher.matches(RAW, hasher.hash(RAW))).isTrue();
  }

  @Test
  void verifyRejectsTheWrongPassword() {
    assertThat(hasher.matches("not-the-password", hasher.hash(RAW))).isFalse();
  }

  @Test
  void hashesAreSaltedSoTheySharedInputDiffer() {
    assertThat(hasher.hash(RAW)).isNotEqualTo(hasher.hash(RAW));
  }

  @Test
  void verifyIsNullSafe() {
    assertThat(hasher.matches(null, hasher.hash(RAW))).isFalse();
    assertThat(hasher.matches(RAW, null)).isFalse();
  }

  @Test
  void hashRejectsNull() {
    assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
