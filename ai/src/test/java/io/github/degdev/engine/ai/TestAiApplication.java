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
package io.github.degdev.engine.ai;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only bootstrap. {@code ai} is a library jar with no runnable application of its own, so the
 * persistence integration slice needs a {@code @SpringBootConfiguration} to start a context
 * against. No opt-in controller exists in this module yet, so (unlike {@code TestAuthApplication})
 * a blanket scan of {@code io.github.degdev.engine.ai} is safe.
 */
@SpringBootApplication(scanBasePackages = "io.github.degdev.engine.ai")
public class TestAiApplication {}
