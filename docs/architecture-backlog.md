# Architecture backlog

Requirements/ideas needing alignment before implementation — worked through per
`docs/process/prompting-methodology.md` → Type 6 (ARCHITECTURAL DISCUSSION → BACKLOG). Not a task
list; a holding area for design questions until a real trigger resolves them.

---

## BL-001 — App-module extension points (domain hook mechanism)

**Status:** To align (deferred, not designed)

**Context:** Milestone 03 (`app-admin-shell`) originally scoped an "extension points" mechanism in
the `app` module, letting the domain layer (milestone 07, `homeservice-*`) hook into the portal
shell (e.g. the order-submission form). The 03-app-host-boot recon (2026-07) found this prose
already living ahead of code in `app/pom.xml` and `app/package-info.java` — no interface, SPI, or
documented contract exists — and reclassified it here rather than building it now.

**Problem:** Designing a generic extension mechanism today would be built against exactly ONE named
future consumer (the milestone-07 order form), which doesn't exist yet. That's the textbook
"infrastructure built speculatively is a defect" case (extract-don't-predict; the project's own
`[ready]` threshold requires 2+ real examples before extracting a pattern/skill). Guessing the
contract shape now all but guarantees a rework once the real consumer shows up.

**Idea (not locked, just the current guess):** likely something in the family of a
`@ConditionalOnBean`-gated extension-point interface + auto-config, mirroring the engine's existing
mounting pattern (`docs/playbooks/engine-screen-mounting.md`) — but this is a guess, not a decision;
do not build from this paragraph.

**Open questions (resolve when the trigger below fires, not before):**
- What does the domain layer actually need to inject — a full route, a component slot, a data
  provider, an event hook? Unknown until the order-form recon (milestone 07) states it concretely.
- Is one consumer enough to design from, or does the `[ready]` 2-example threshold mean waiting for
  a second domain consumer before extracting the mechanism at all?

**Scope:** Engine (`app` module) — no domain code.

**Decision needed by:** Not urgent — resolves naturally when milestone 07's order-form recon reaches
the "how does this attach to the app portal" question. Revisit this entry then, not before.

---

## BL-002 — CMS image/MinIO attachment

**Status:** To align (deferred, not designed)

**Context:** Milestone `03-cms-screen` added a generic content-block mechanism (`ContentBlock` +
`ContentTranslation`, text only). MinIO exists today only as an unwired docker-compose service
(`stack.yml:77-93`) — zero SDK dependency, zero Java client, zero consumer anywhere in either repo.
Wiring an S3/MinIO SDK into the first CMS pass would be infrastructure-ahead-of-need.

**Problem:** No concrete consumer has stated what an image attachment needs yet (single hero image?
gallery? per-translation images?) — designing the storage contract now would guess at that shape.

**Idea (not locked, just the current guess):** a sibling entity/column on `ContentBlock` or
`ContentTranslation` holding a MinIO object key, resolved to a URL at read time — but this is a
guess, not a decision; do not build from this paragraph.

**Scope:** `common` (CMS entities/service) — no domain code.

**Decision needed by:** Not urgent — resolves when a real screen (admin CMS editor, milestone 03
pass 4, or a domain consumer) states a concrete image requirement. Revisit this entry then, not
before.

---

## BL-003 — Migration ownership in a multi-runnable modular monolith (DECIDED)

**Status:** Decided (operator-ratified 2026-07-10, in design discussion) — ready to implement, not
deferred. Recorded here for provenance because it is a load-bearing architectural decision. NOTE:
the decision was made by the operator in a design conversation AFTER the (read-only) recon, not by
the recon itself — the recon deliberately made no pick; the operator chose the consolidate-in-common
option afterward.

**Trigger (the real defect that surfaced it):** When `app` was first started with a datasource
profile (03-app-landing-cms, to read CMS content), Flyway failed validation on boot:
`Detected applied migration not resolved locally: 2, 3, 4`. Root cause: one shared Postgres with one
shared `flyway_schema_history`, but each runnable has a DIFFERENT subset of migrations on its
classpath — `admin` depends on `common`+`auth` (sees V1, cms, V2, V3, V4), `app` depends on `common`
only (sees V1, cms). When `app` runs Flyway it finds `auth`'s V2–V4 recorded as applied in the shared
history but absent from its own classpath → validation fails. The same wall blocks the near-future
case where `app` grows its OWN entities and needs its own migrations applied.

**Options considered (recon + live Flyway 12.4 / Boot 4.1 verification via context7):**
- *Per-module history table* (`spring.flyway.table=<module>_...` + baseline): Boot 4.1 autoconfigures
  exactly ONE Flyway bean per context, and `common` migrations are needed by every runnable — so this
  needs a separately-shared common history alongside each runnable's own, which the single-bean
  autoconfig can't do without going fully programmatic. Rejected.
- *Schema-per-module*: not blocked by any cross-module FK today (recon confirmed none exist), but
  Postgres FKs can't cross schemas — the milestone-07 domain will almost certainly FK into
  `auth.account`, which would break this. Rejected (fails on the first cross-module FK).
- *Programmatic multiple Flyway instances* (`@DependsOn` before EMF, hand-rolled per module): highest
  control, zero repo precedent, re-implements the migrate-before-EntityManagerFactory ordering Boot
  gives for free. Rejected as unnecessary complexity for our structure.

**Decision:** ALL migrations live in one place — `common/src/main/resources/db/migration` — regardless
of which module's tables they create. Every runnable depends on `common`, so every runnable sees the
IDENTICAL full migration set; there is no classpath-subset difference, so the shared history never
disagrees with any runnable's local set. The bug dies by construction, not by suppression (NO
`ignoreMigrationPatterns`, NO validate-off — those were explicitly rejected as band-aids).

**Why this doesn't violate the module direction rule (`common ← auth ← admin`):** the rule governs
CODE dependencies (who imports whose classes). Moving `.sql` DDL into `common` creates NO code edge —
`Account`/`Role` classes stay in `auth`, no `common` class imports `auth`. The database SCHEMA is a
single system-wide resource, not any one module's property; `common`, being the shared-by-all module,
is the correct home for the shared-by-all schema. `common` "knowing" every module's tables is its job
as the common module, not a leak.

**Consequences / implementation scope (for the follow-up plan):**
- Move `auth`'s `V2`/`V3`/`V4` from `auth/.../db/migration` into `common/.../db/migration`.
- Remove the `FlywayAutoConfiguration` exclusion from `app` (and any future runnable) — with the full
  set on the classpath there is nothing to conflict on; every runnable applies the same migrations
  against the shared history idempotently.
- Naming: `common` already uses the timestamp convention; `auth`'s V2–V4 are small sequential ints.
  Decide during the plan whether to keep the mix (Flyway sorts V2 < V20260710… correctly) or rename —
  but NEVER rename an ALREADY-APPLIED migration against a live DB without a baseline/repair step.
- Handle the already-applied history: the dev DB already has V2–V4 applied under the old layout; the
  plan must verify Flyway doesn't see the moved files as changed (checksum/location), or reset the dev
  DB cleanly. Not a blind file move.
- Coverage gap (recon finding D): NO existing test exercises the multi-runnable-shared-DB scenario —
  every integration test spins its own fresh single-classpath container. The implementation MUST add a
  test that migrates a persistent DB from one classpath then connects with a narrower one, or this
  bug class stays untested.

**Scope:** Engine (`common` migrations + runnable properties + a new integration test). No domain code.

---

## BL-004 — Admin nav as data (sidebar sections generated, not hand-coded)

**Status:** To align (deferred, not designed). Raised during 06-ai-slice recon (2026-07-12) as a
tangent, not this milestone's scope — logged here per extract-don't-predict rather than designed now.

**Idea:** replace the admin sidebar's programmatic menu entries with a data-driven registry (each
screen/module contributes a nav section descriptor) instead of hand-editing a shell component per new
screen. No consumer has stated the concrete contract yet; revisit once 2+ real screens make the
hand-coded approach visibly repetitive.

**Scope:** `admin` module (frontend, `nobilis-platform-front`), milestone 03 territory — not backend,
not this milestone.

---

## BL-005 — Production routing: admin SPA and backend must be same-origin

**Status:** To align (deferred, not designed). Part of the deferred deployment/infra track
(milestone 07 / homeservice deploy). Logged per extract-don't-predict — surfaced by a real defect,
not designed ahead.

**Idea:** the admin frontend calls the backend via relative paths (`/auth/admin/login`,
`/auth/admin/remint`, `/api/admin/*`), which only resolve if the SPA and the API are served from
the same origin. In dev this works because `projects/admin/proxy.conf.json` bridges the Angular
dev-server (`:4200`) to the backend (`:8080`). In prod there is no equivalent bridge — neither repo
contains a Dockerfile, reverse proxy, or nginx config — so nothing routes the SPA's relative
`/auth/**` and `/api/admin/**` calls to the backend once the dev-server proxy is out of the picture.
A deployed admin app would fail to reach the backend at all; this is not specific to the
401-refresh feature, it breaks the entire admin API surface in prod (surfaced during the
silent-token-refresh feature, 2026-07, via the remint 401 symptom, but the root gap is broader).
When prod routing is designed, same-origin serving of the admin SPA + backend is required (a
reverse proxy in front of both, or the backend serving the built SPA as static assets under the
same origin) — the exact mechanism is a deployment-track decision, not designed here.

**Scope:** deployment/infra, spans both repos (frontend relative-path assumption + backend/prod
serving topology) — not a code change in any current milestone.
