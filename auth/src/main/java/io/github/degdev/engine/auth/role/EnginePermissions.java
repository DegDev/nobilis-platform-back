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
package io.github.degdev.engine.auth.role;

import java.util.Set;

/**
 * The engine's own permission catalog: atomic authorities the engine gates its own capabilities on.
 * Permissions are deliberately plain {@code String} constants, not a Java enum, so a domain product
 * can ship its own catalog and add authorities without editing (or recompiling) the engine — the
 * value stored in {@code role_permission} is just the constant's string.
 *
 * <p>This is a utility class: the private constructor is an intentional exception to the
 * no-boilerplate house style (there is nothing to instantiate). Domain catalogs follow the same
 * shape in their own modules; the engine ships only the authorities it needs for its own features.
 */
public final class EnginePermissions {

  /** Manage engine settings (read/write the settings store). */
  public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

  /** Manage accounts (create/administer identities, realms, and role assignments). */
  public static final String ACCOUNT_MANAGE = "ACCOUNT_MANAGE";

  /** Manage CMS content blocks (create/publish/translate/delete). */
  public static final String CONTENT_MANAGE = "CONTENT_MANAGE";

  /** Manage notification types, templates, and their translations (config only, no dispatch). */
  public static final String NOTIFICATIONS_MANAGE = "NOTIFICATIONS_MANAGE";

  /** Manage AI-profile catalog/config (provider selection, params, secrets, health-check). */
  public static final String AI_MANAGE = "AI_MANAGE";

  /**
   * Every engine permission, as a single source of truth. Grow this alongside the constants above
   * so a "grant everything" caller (e.g. the config-admin) never drifts out of sync with the
   * catalog.
   */
  public static final Set<String> ALL =
      Set.of(SETTINGS_MANAGE, ACCOUNT_MANAGE, CONTENT_MANAGE, NOTIFICATIONS_MANAGE, AI_MANAGE);

  private EnginePermissions() {}
}
