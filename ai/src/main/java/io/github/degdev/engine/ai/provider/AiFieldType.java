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
package io.github.degdev.engine.ai.provider;

/**
 * The control shape an {@link AiProviderField} renders as. Persisted as {@code varchar} via
 * {@code @Enumerated(STRING)}, never a native Postgres enum.
 */
public enum AiFieldType {

  /** A single-line text input. */
  STRING,

  /** A numeric input, optionally bounded by {@code minValue}/{@code maxValue}. */
  NUMBER,

  /** A toggle. */
  BOOLEAN,

  /** A single choice from the field's {@link AiProviderFieldOption} set. */
  SELECT,

  /** Multiple choices from the field's {@link AiProviderFieldOption} set. */
  MULTISELECT
}
