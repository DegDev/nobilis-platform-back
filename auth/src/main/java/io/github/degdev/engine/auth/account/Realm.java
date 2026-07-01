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
package io.github.degdev.engine.auth.account;

/**
 * A coarse membership gate, orthogonal to roles and permissions: which side of the engine an
 * account may enter at all. An account can hold several realms at once. Persisted as {@code
 * varchar} via {@code @Enumerated(STRING)} (never a native Postgres enum — see {@code
 * docs/conventions.md}).
 */
public enum Realm {

  /** Access to the administrative side of the engine. */
  ADMIN,

  /** Access to the public portal side of the engine. */
  CLIENT
}
