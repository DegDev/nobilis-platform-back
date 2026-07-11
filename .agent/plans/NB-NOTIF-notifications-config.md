# Plan: Notifications config (backend)

> **RECONSTRUCTED POST-HOC (2026-07-11).** This feature was built without running `/plan` first
> (GLM skipped it). This file is reconstructed from the git history (`03-notifications-config`
> commits `485691c`, `886406a`, `a3e79dd`, merged in PR #17) — there is no `docs/sources-log.md`
> entry for this feature and no pre-code recon file, confirming the skip. The **Recon** and **Open
> questions** sections are therefore N/A, not genuine pre-code sections. Everything else (Goal,
> Architectural decisions, Spec, Tasks, DoD, Lessons) is factual — it describes what was actually
> shipped, plus what review+smoke caught before/around landing.

## Feature ID
NB-NOTIF

## Scope
fullstack — backend part

**Paired plan:** `NB-NOTIF-notifications-config.md` in the frontend repo
(`/home/deg/www/nobilis-platform-front/.agent/plans/NB-NOTIF-notifications-config.md`).

## Applicable playbook
- `docs/playbooks/engine-screen-mounting.md` — the mounting recipe (service auto-config gated on
  `EntityManagerFactory`, unconditional `@AutoConfigurationPackage`, controller auto-config +
  mandatory component-scan exclude) applies in full: `NotificationsAutoConfiguration` (service,
  `common`) + `NotificationsWebAutoConfiguration` (controllers, `admin`) mirror the settings/roles/
  accounts/CMS shape. This feature is also the source of the playbook's "every DTO-returning
  endpoint needs a MockMvc test — LIST especially" addendum (see Lessons below).

## Recon (before code)
**N/A — reconstructed post-hoc.** No pre-code recon file was written. The premises a real recon
would have surfaced are recorded as resolved facts under Decisions / Architectural decisions below.

## Decisions (as-built, locked forks)
1. **Two entities, not one wide table:** `NotificationType` + `NotificationTemplate`, plus a child
   `NotificationTemplateTranslation` table.
2. **Engine = mechanism only:** `NotificationType.key` is free-form, no seed data, no type-key enum.
3. **`Transport` is a `varchar` enum column** (`EMAIL`, `TELEGRAM`, `SMS`), never a native Postgres
   enum type.
4. **Translations are a child table:** `subject` nullable, `body` not-null, `ru`/`ro` locales.
5. **New permission `NOTIFICATIONS_MANAGE`**, not a reuse of an existing one.
6. **One `/notifications` route, tabbed Types/Templates** (frontend-visible shape driving the
   backend's two-controller split).

## Goal
Give the engine a config/data layer for notification types, per-type/transport templates, and
their per-locale translations — mounted via the existing engine-screen-mounting recipe, gated by a
new `NOTIFICATIONS_MANAGE` permission — so a later milestone (04) can add dispatch on top without
any schema change. This pass ships configuration only; no transport actually sends anything yet.

## Architectural decisions (as-built, locked forks — full detail)

1. **Two entities: `NotificationType` + `NotificationTemplate`, plus a child translation table.**
   `NotificationType` (key, enabled, description) 1—N `NotificationTemplate` (type + transport,
   unique per pair) 1—N `NotificationTemplateTranslation` (locale, subject nullable, body
   not-null, unique per template+locale). Cascade delete: removing a type drops its templates and
   their translations (`V20260710150000__notifications.sql`).
2. **Engine ships the mechanism only — `NotificationType.key` is free-form, no seed data.** No
   enum/catalog of type keys in the engine; a domain product inserts its own keys
   (`order.created` etc.) later. Mirrors the engine/domain boundary rule (`CLAUDE.md`).
3. **`Transport` is a `varchar` enum column** (`EMAIL`, `TELEGRAM`, `SMS`), never a native
   Postgres enum type — the engine's standing convention for enum-shaped columns (migration
   `transport varchar(20) not null`).
4. **Translations are a child table, not columns on the template.** `subject` nullable,
   `body` not-null; `ru`/`ro` are the two locales exercised. Matches the CMS content-block
   translation shape (`ContentBlockTranslation`) rather than inventing a new pattern.
5. **New permission `NOTIFICATIONS_MANAGE`** (`EnginePermissions.java:42`), added to the
   `SETTINGS_MANAGE, ACCOUNT_MANAGE, CONTENT_MANAGE` catalog set. Both controllers are gated by
   it.
6. **Two REST controllers under `/admin/api/notification-types` and
   `/admin/api/notification-templates`**, matching the frontend's Types/Templates tab split
   (one `/notifications` route, tabbed — see paired frontend plan). Template endpoints are
   type+transport-scoped (`/{typeKey}/{transport}`, `/{typeKey}/{transport}/translations/{locale}`),
   not a flat `/templates/{id}`.
7. **Screen is config/data only — no dispatch.** Sending notifications through a transport is
   milestone 04 (integration worker); this pass only manages types/templates/translations.

## Spec — files created (backend)

- **Model (`common`):** `NotificationType`, `NotificationTemplate`, `NotificationTemplateTranslation`,
  `Transport` (enum), `NotificationTypeRepository`, `NotificationTemplateRepository`,
  `NotificationConflictException`, `NotificationTypeNotFoundException`,
  `NotificationsAutoConfiguration`, `NotificationsService` (357 lines — type/template/translation
  CRUD, LazyInit-safe DTO assembly).
- **Web (`admin`):** `NotificationTypeController`, `NotificationTemplateController`,
  `NotificationTypeDto`/`NotificationTypeCreateRequest`/`NotificationTypeUpdateRequest`,
  `NotificationTemplateDto`/`NotificationTemplateCreateRequest`/
  `NotificationTemplateTranslationRequest`, `NotificationsWebAutoConfiguration`.
- **RBAC:** `NOTIFICATIONS_MANAGE` added to `EnginePermissions`; `AdminApplication` scan-exclude
  extended for the two new controllers.
- **Migrations:** `V20260710150000__notifications.sql` (schema);
  `V20260710150001__seed_admin_missing_permissions.sql` (backfill — see Lessons, defect 3).
- **Side effect:** `UnsupportedLocaleException` moved from the `cms` package to a shared `i18n`
  package (reused by notifications translations, same locale-validation shape as CMS).

## Tasks (atomic — as executed, one pass each; timestamps as committed, not necessarily true
authorship order — see Lessons for why 485691c/886406a predate a3e79dd)
1. `485691c` — fix(admin): include `NOTIFICATIONS_MANAGE` in the `RolesCrudIntegrationTest`
   permission-catalog assertion.
2. `886406a` — fix(auth): backfill the seeded `ADMIN` role with `CONTENT_MANAGE` +
   `NOTIFICATIONS_MANAGE` via a **new** migration (not an edit to the already-applied seed).
3. `a3e79dd` — feat(notifications): the full backend slice (model, service, two controllers,
   migration, permission, LazyInit-safe DTO loading, type-filtered template listing,
   `NotificationsCrudIntegrationTest` — 379 lines).

## Testing strategy (as-built)
- `NotificationsCrudIntegrationTest` (`admin`, MockMvc + Testcontainers Postgres): anonymous access
  rejected by the auth contour; full type CRUD; duplicate type key → 409; full template
  create/upsert-translation/remove-translation/delete lifecycle; **list without type filter
  exposes type key**; **list filtered by type excludes other types** (the regression test for
  defect 5 below); unsupported locale → 400; type listing is paged.
- `RolesCrudIntegrationTest` — permission-catalog assertion extended to the fourth permission
  (defect 1).
- `AdminRoleSeedIntegrationTest` — updated for the backfilled seed (defect 3).

## DoD (as-built, all met)
- Two entities + child translation table mount via the engine-screen-mounting recipe (service
  gated on EMF, controllers gated on the service, both in the host's scan-exclude).
- `NOTIFICATIONS_MANAGE` gates both controllers; seeded into the `ADMIN` role via a new migration.
- List endpoints return LazyInit-safe DTOs (associations force-initialized in-tx).
- Type-filtered template listing actually filters (regression-tested).
- `mvn -B verify` green (`common`, `auth`, `admin`).

## Lessons / defects caught in review + smoke

Six post-build defects surfaced after the initial build, before/around landing — none were caught
by the first green test run. Recorded here (and folded into `docs/playbooks/engine-screen-mounting.md`
and the frontend `angular22-verification` skill) so the next screen doesn't repeat them:

1. **Permission-catalog test went stale.** `RolesCrudIntegrationTest`'s catalog assertion was
   written before `NOTIFICATIONS_MANAGE` existed, so it silently missed the fourth permission
   until updated (`485691c`). Adding a permission constant means updating every test that
   enumerates the catalog, not just the constant's own usage sites.
2. **Test method names needed to move off snake_case.** Method names in the new
   `NotificationsCrudIntegrationTest` were normalized to the repo's `camelCase` convention
   (`listTemplatesFilteredByTypeExcludesOtherTypes`, not a snake_case draft) — a style-consistency
   catch, not a logic bug.
3. **An already-applied migration cannot be edited.** `CONTENT_MANAGE` and `NOTIFICATIONS_MANAGE`
   were missing from the seeded `ADMIN` role's `role_permission` rows. The seed migration
   (`V20260710120002`) was already applied, so editing it in place would break its checksum on
   every DB where it had run. Fixed with a **new** migration
   (`V20260710150001__seed_admin_missing_permissions.sql`) instead — see the Liquibase/Flyway
   "never edit an applied changeset" rule in `CLAUDE.md`, same principle applies to Flyway here.
4. **`LazyInitializationException` on the template list endpoint.** `Page.map` maps DTOs *after*
   the service transaction closes (`open-in-view=false`); the DTO read a lazy association that
   only a list-path test (not a get-one test) exercises. Fixed by force-initializing the needed
   associations in-tx before mapping — the exact defect now codified in
   `docs/playbooks/engine-screen-mounting.md`'s "every DTO-returning endpoint needs a MockMvc
   test — LIST especially" section.
5. **`listTemplatesByType` ignored its own filter.** The type-scoped template listing endpoint
   accepted the `typeKey` path/query parameter but the service call underneath didn't apply it,
   returning all templates regardless of type. Caught by a dedicated regression test
   (`listTemplatesFilteredByTypeExcludesOtherTypes`), now permanent in the suite.
6. **Frontend translations dialog `formControlName` without an enclosing `[formGroup]`** —
   `NG01050` at runtime, build/Vitest stayed green. Backend-adjacent only in that it's part of the
   same feature; full detail and the fix (signal-based dialog pattern) is in the paired frontend
   plan and the `angular22-verification` skill.

## Open questions
**N/A — reconstructed post-hoc.** No open forks were left; the six items above are historical
defects, not undecided design questions.

## Related features / dependencies
- Depends on `03-app-admin-shell` (admin host, RBAC, the engine-screen-mounting recipe) and
  `NB-CMS-cms-content-blocks` (translation-child-table precedent, `i18n.UnsupportedLocaleException`).
- Feeds forward: dispatch (sending through a transport) is milestone 04, not built here.
- Cross-repo: paired frontend plan `NB-NOTIF-notifications-config.md` in `nobilis-platform-front`.

## Risks (as-built)
- **No `/plan` discipline on this pass** is itself the risk this document backfills — the six
  defects above are evidence of what skipping recon/spec costs (a stale test, an edited-migration
  near-miss, a LazyInit runtime failure, a silently-ignored filter, a broken dialog binding), none
  of which a genuine pre-code plan would have prevented outright, but a couple (the LazyInit list
  trap, the filter-ignored bug) are exactly the class of thing a testing-strategy section written
  *before* code tends to catch by naming the test up front.
