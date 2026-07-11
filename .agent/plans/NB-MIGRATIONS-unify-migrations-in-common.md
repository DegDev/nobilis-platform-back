# Plan: Unify all Flyway migrations into `common`

## Feature ID
NB-MIGRATIONS

## Scope
backend

## Applicable playbook
None fits exactly — this is a one-off architectural resource move, not a recurring task class.
Closest anticipated playbook is `docs/playbooks/recon-first.md` ("recon before touching coupled or
risky code") for the spirit (this plan is itself the recon-first pass), but it is not `[ready]` and
its content doesn't cover migration ownership. Flagging for discussion rather than forcing a fit;
if this pattern recurs (e.g. a future module split), extract a playbook from this instance.

## Goal
Move all Flyway migrations into `common/src/main/resources/db/migration` (the one and only
migration location every runnable's classpath includes) and remove the per-runnable
`FlywayAutoConfiguration` exclusion misalignment, so `admin` and `app` always see and apply the
identical migration set against the shared Postgres/`flyway_schema_history`. Fixes the real boot
failure (`Detected applied migration not resolved locally: 2, 3, 4`) by construction, per the
ratified decision in `docs/architecture-backlog.md` BL-003.

## Architectural decisions

Source: `docs/architecture-backlog.md` BL-003 (DECIDED, operator-ratified 2026-07-10). Full
rationale and rejected alternatives (per-module history table, schema-per-module, programmatic
multi-Flyway) are recorded there — not re-litigated here.

**Locked:**
1. All migration `.sql` files live in `common/src/main/resources/db/migration`. Auth's Java classes
   (`Account`, `Role`, etc.) stay in `auth` — this is a resource move only; no new `common`→`auth`
   code dependency is created (schema is a system-wide resource, `common` is its correct home).
   `common` already owns the Flyway dependency (`common/pom.xml:37-44` — `spring-boot-flyway` +
   `flyway-database-postgresql`), consistent with this.
2. The `spring.autoconfigure.exclude` list in `admin`/`app` `application.properties` currently
   excludes `FlywayAutoConfiguration` by default, and their `application-local.properties` clears
   the exclude list to enable DB/Flyway locally (recon-confirmed: `admin/src/main/resources/
   application.properties:10`, `admin/src/main/resources/application-local.properties:14`,
   `app/src/main/resources/application.properties:12`, `app/src/main/resources/
   application-local.properties:14-15` — pattern is already identical between admin and app, this
   is NOT a divergence to fix). What changes is what's now UNDER that already-enabled Flyway: with
   the full migration set on every runnable's classpath via `common`, `app`'s local/DB profile can
   enable Flyway (as admin's already does) with nothing left to conflict on.
3. This is a prerequisite for `03-app-landing-cms` (blocked: `app` couldn't boot with a datasource
   profile) but lands as its own branch/PR first, independent of that feature branch.

**GATE-0 — resolved (operator decision):** Reset the dev DB. `stack.yml:107-110` `pg_data` is a
named Docker volume (not a bind-mount) — trivially dropped (`docker compose down -v` or
`docker volume rm`, run by the operator, not this plan). Resetting also re-triggers
`stack.yml:24`'s `.docker/postgres/initdb/10-demo-back-compat.sql` on the fresh volume — expected,
not a side effect to guard against.

**Naming — resolved (tied to the reset decision):** Since the dev DB is reset (no live applied
history to preserve), rename auth's `V2`/`V3`/`V4` to the timestamp convention already used by
`common` (`V20260710143000__cms_content.sql`) for one consistent naming scheme going forward. Use
fresh `date +%s`-derived timestamps at implementation time (per this repo's global migration-naming
rule — never hand-increment or copy an existing number), assigning them in the SAME relative order
the files already have (V2's content gets the earliest of the three new timestamps, V4's the
latest) so migration order is preserved. This is a rename-before-first-apply, not a rename of an
already-applied-and-preserved migration, so it does not fall under the "never rename an applied
migration" trap — the reset makes these unapplied-from-Flyway's-perspective going forward.

## Files to create / change

### Backend
Modules touched: `common`, `auth`, `admin`, `app`.

- `common/src/main/resources/db/migration/V<ts1>__account_identity_realm.sql` — moved + renamed
  from `auth/src/main/resources/db/migration/V2__account_identity_realm.sql` (content
  byte-identical, filename only).
- `common/src/main/resources/db/migration/V<ts2>__role_permission_account_role.sql` — moved +
  renamed from `auth/src/main/resources/db/migration/V3__role_permission_account_role.sql`.
- `common/src/main/resources/db/migration/V<ts3>__seed_admin_role.sql` — moved + renamed from
  `auth/src/main/resources/db/migration/V4__seed_admin_role.sql`.
- `auth/src/main/resources/db/migration/` — removed (empty after the move; delete the directory if
  Maven/git leaves nothing else in it).
- `app/src/main/resources/application-local.properties` — verify/align so its DB-enabling profile
  matches `admin`'s pattern (recon found them already identical; this task is a confirmation step,
  not necessarily an edit — see GATE-0/Tasks).
- `auth/src/test/java/io/github/degdev/engine/auth/role/AdminRoleSeedIntegrationTest.java` —
  recon-flagged coupling: this test asserts on the `V4` seed by name/content (test method
  `v4SeedsTheDefaultAdminRoleWithTheTwoEnginePermissions`, line 59). After the rename, update any
  hardcoded version-number reference in the test/assertions if present; re-verify it still passes
  (relies on Boot's default `classpath:db/migration` scan transitively resolving `common`'s new
  files — should work unchanged, but confirm, don't assume).
- NEW integration test (module TBD at implementation time — recon suggests `admin`, the widest
  classpath, or a new top-level module-spanning test) — the multi-runnable-shared-DB scenario:
  migrate against a persistent Testcontainers Postgres with one classpath, then boot/validate with
  another (now-identical) classpath against the same DB, proving no `flyway_schema_history`
  mismatch. Closes the coverage gap noted in `docs/architecture-backlog.md:120-123` (recon confirmed
  zero existing tests do this — all 12 existing `@Container` integration tests use fresh,
  single-classpath containers, e.g. `AccountServiceIntegrationTest.java:50-51`,
  `PortalContentIntegrationTest.java:56-57`).
- `docs/sources-log.md` — new entry: the consolidation and why, pointing to BL-003.

## Open questions
None blocking — GATE-0 and naming are resolved above. One implementation-time decision left
open on purpose (not architectural): which module hosts the new multi-runnable-shared-DB test —
left to the implementer's judgment per the recon's suggestion (admin, widest classpath).

## Testing strategy

### Backend
- Unit tests: none expected to change (no Java logic touched, only resource location + properties).
- Integration tests:
  - Existing Testcontainers-based tests in `common`, `auth`, `admin`, `app` (12 classes, recon-
    confirmed) must stay green — each spins a fresh container, so the moved/renamed files must still
    resolve on every affected classpath (`auth`'s tests get them transitively via `common`, same as
    `admin`/`app` already do for `common`'s own migrations).
  - `AdminRoleSeedIntegrationTest` and `AccountServiceIntegrationTest`/`AccountIdentityRealmIntegrationTest`/
    `RolePermissionIntegrationTest` (auth module) re-verified explicitly post-rename.
  - NEW test proving the original failure is gone: `app` boots with a datasource profile enabled,
    Flyway validates clean, against a DB that previously had migrations applied via a different
    classpath subset.
  - `mvn -B verify` green across all modules.
  - Diff grep: no `ignoreMigrationPatterns`, no `validate=false`, no other Flyway band-aid
    introduced anywhere (explicitly rejected in BL-003).

## Related features
- Prerequisite for `NB-LANDING` / `03-app-landing-cms` (paired plan:
  `.agent/plans/NB-LANDING-app-landing-cms.md` in this repo, and its frontend pair in
  `nobilis-platform-front/.agent/plans/`) — that feature is blocked on `app` being able to boot
  with a datasource; unblocked by this fix but landed independently.
- Architectural source: `docs/architecture-backlog.md` BL-003.

## Risks
- Renaming migration files, even pre-apply-on-a-reset-DB, is a mechanical step that's easy to get
  the relative ordering wrong on (V2/V3/V4 → three fresh timestamps) — verify final Flyway apply
  order matches the original V2 < V3 < V4 sequence before considering the move done.
- The dev DB reset is destructive to local dev data — confirm with the operator immediately before
  running `docker compose down -v` / `docker volume rm` (this plan does not execute that; it's a
  manual step at implementation time, called out again in Tasks/DoD).
- `AdminRoleSeedIntegrationTest`'s coupling to `V4`'s content/name is the one test most likely to
  need a small edit post-rename — budget time for it explicitly rather than assuming a silent pass.
