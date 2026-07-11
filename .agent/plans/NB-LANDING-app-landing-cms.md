# Plan: App-portal landing reads CMS (backend)

## Feature ID
NB-LANDING

## Scope
fullstack — backend part

**Paired plan:** `NB-LANDING-app-landing-cms.md` in the `nobilis-platform-front` repo.

## Branch
Cut fresh from `origin/main`: `git fetch origin && git switch -c 03-app-landing-cms origin/main`.
Frontend repo cuts the same-named branch (not done by this plan step) — keep both repos on the
identical branch name for this vertical slice, per branch discipline (no slash, upstream tracks
`origin/<same-name>`).

## Applicable playbook
- `docs/playbooks/engine-screen-mounting.md` is explicitly **not applicable** despite looking close:
  its own header scopes it "backend only ... a JVM / Spring Boot auto-configuration pattern" and all
  three extracted instances (settings/roles/accounts) mount a NEW service+controller into the
  `admin` host. This pass mounts an EXISTING service (`ContentBlockService`, already auto-configured
  in `common`) into a DIFFERENT host (`app`) that has never had a datasource before — same underlying
  Spring mechanism (`@ConditionalOnBean(EntityManagerFactory)`, datasource profile) but a new host
  target and a public/anonymous controller, not an admin one. Per the operator's rule, no new
  playbook is written this pass; the gap (first non-admin host mount, first public path) is flagged
  for a future extraction once a second example exists.
- fullstack: paired frontend plan flags the same gap.

## Goal
Give the `app` (public portal) host a datasource so it can mount the already-existing
`ContentBlockService`, and add one public, anonymous, read-only endpoint so the portal's landing
page can render CMS content — without adding any migrations, admin logic, or write path to `app`.

## Architectural decisions

(Locked by the operator after RECON; forks resolved, not re-litigated here.)

1. **Transport = in-process mount, not HTTP-to-admin.** `app` and `admin` are two runnables of one
   modular monolith over one shared Postgres. `app` mounts `common`'s `ContentBlockService` directly
   in its own process, the same way `admin` already does — not via a new public controller on the
   `admin` host plus an `app:8082 → admin:8080` network call. Rejected: introducing inter-host HTTP
   topology that doesn't exist for any other engine capability today.

2. **`app` becomes stateful via a datasource profile — the identical mechanism `admin` already
   uses, not a bespoke "read-only JPA" construct.**
   - Recon-confirmed: `app/src/main/resources/application.properties:3-10` currently excludes
     DataSource/HibernateJpa/DataJpaRepositories/Flyway autoconfiguration — a deliberate "stateless
     portal boot" decision, now superseded because the portal has a real reason to read the DB (CMS
     content exists and needs rendering).
   - Mirror `admin`'s pattern exactly: `application-local.properties` sets
     `spring.autoconfigure.exclude=` (empty — re-enables everything including Flyway), plus
     `spring.datasource.*`, `ddl-auto=validate`, `open-in-view=false`. `app` gets an equivalent file,
     committed as `application-local.properties.example` with placeholders (real file gitignored) —
     same runnable-owns-its-config pattern `admin` follows.
   - Recon-confirmed: `ContentBlockAutoConfiguration.java:41` conditions `ContentBlockService` on
     `@ConditionalOnBean(EntityManagerFactory)`. Once `app` has a datasource, the EMF exists and the
     service mounts automatically — **no new mounting code in `common`**, `app` just gains the
     datasource.

3. **Migrations stay in the library modules (`common`/`auth`); `app` creates none.** Recon-confirmed:
   all `.sql` migrations live in `common` (V1 baseline, `V20260710143000` cms) and `auth` (V2–V4); no
   runnable module carries its own migrations today. When `app` boots with the datasource profile,
   in-process Flyway applies the same classpath migrations `admin` applies, against the same shared
   `flyway_schema_history` table — idempotent regardless of boot order (if `admin` already applied
   them, `app` sees them as already-applied). This preserves the existing principle: migrations
   belong to libraries, never to hosts.

4. **The public read controller lives in `app`, not `common`, not `admin`.** Recon-confirmed: `app`
   has no security contour at all — `AdminContourFilter`/`ContourSecurityConfiguration` are
   admin-only and physically absent from the `app` process (`app/pom.xml:19-34` has no admin
   dependency, no security starter). A controller placed in `app` is therefore anonymous/public by
   construction — there is no contour to bypass. Mirror the existing `app/.../health` package
   placement for a new `app/.../content` (or `.../portal`) feature package.

5. **`readPublished` is ready as-is; no admin logic is copied.** Recon-confirmed:
   `ContentBlockService.java:184-196` — `Optional<String> readPublished(key, locale)`, PUBLISHED-only,
   ru-fallback happens INSIDE the service, documented as "never errors". The new controller only
   calls this method and maps its `Optional` to an HTTP response; it does not duplicate any
   admin-side draft/publish/permission logic.

## Files to create / change

### Backend (`app` module)
- `app/src/main/resources/application-local.properties` (gitignored, real values) and
  `application-local.properties.example` (committed, placeholders) — new datasource profile
  mirroring `admin/src/main/resources/application-local.properties`: empty
  `spring.autoconfigure.exclude=`, `spring.datasource.url/username/password`, `ddl-auto=validate`,
  `open-in-view=false`. `server.port=8082` unchanged.
- `app/src/main/resources/application.properties` — update the existing comment that currently
  states the portal touches no database (recon: needs to stop contradicting the new stateful-profile
  reality); default (no-profile) behavior itself is unchanged — still excludes
  DataSource/HibernateJpa/DataJpaRepositories/Flyway.
- New package alongside `app/.../health`, e.g. `app/.../content/PortalContentController.java`:
  `GET /api/content/{key}?locale=` → `ContentBlockService.readPublished(key, locale)` → 200 with the
  body (plain string or a tiny `{key, locale, body}` DTO — decide in BUILD, see paired frontend
  plan's open question 1) on present, 404 on `Optional.empty()` (DRAFT or absent). Anonymous — no
  `@PreAuthorize`, no contour, nothing to configure since `app` has no security starter at all.
- Reuses the `01-common` locale contract as-is: unknown/blank `locale` falls through to
  `ContentBlockService`'s internal ru-fallback — the controller does not validate or error on it.
- New boot test (e.g. `app/.../AppApplicationTest.java` addition or sibling) proving BOTH: the
  stateless default context still boots with `ContentBlockService` **absent**; a context booted with
  the datasource profile active has it **present**.
- New integration test (Testcontainers PG18) for `PortalContentController`: PUBLISHED key → 200 with
  body; DRAFT or absent key → 404 (never 500); unknown locale → 200 with ru-fallback body; request
  with no auth header/token → still 200 (proves anonymous access, no contour interference).

### Frontend
See paired plan `nobilis-platform-front/.agent/plans/NB-LANDING-app-landing-cms.md` — `app.config.ts`
HTTP wiring, `proxy.conf.json`, new portal content API service, `landing` component swap.

## Open questions
1. Exact response shape for `GET /api/content/{key}` (plain body string vs. `{key, locale, body}`
   DTO) — an implementation-level call for the BUILD pass, not an architectural fork; whichever is
   simpler for the single frontend consumer wins. Mirrors the frontend plan's open question 1.
2. Landing CMS key name(s) requested by the frontend (e.g. `landing.hero`) — needs agreement with the
   frontend pass so the backend integration test and the frontend fetch target the same key; seeding
   real content is out of scope either way.

## Testing strategy

### Backend
- Unit tests: `PortalContentController` 404-mapping for `Optional.empty()`, 200 for a present value,
  no auth-related behavior to unit-test (no contour present).
- Integration tests (Testcontainers PG18): PUBLISHED/DRAFT/absent/locale-fallback/anonymous-access,
  as listed above.
- Boot tests: stateless-default (no `ContentBlockService` bean) vs. datasource-profile (bean present)
  — both must pass, proving the flip is additive via profile, not a change to the default boot.

### Frontend
See paired plan.

## Related features
- Cross-repo: paired frontend plan `NB-LANDING-app-landing-cms.md` in `nobilis-platform-front`.
- Depends on: `NB-CMS` (`ContentBlockService`/`ContentBlockAutoConfiguration`/migrations, already
  merged) and `03-app-admin-shell` (the `admin` datasource-profile pattern being mirrored here).
- Playbook gap flagged (not written this pass): first non-admin host mounting a `common` service +
  first public/anonymous engine path. Candidate for extraction after a second real example — see
  `docs/playbooks/README.md`'s "extract, don't predict" principle.

## Risks
- This is the first time `app` gets a real datasource — scope discipline matters: this pass adds
  exactly one datasource profile + one read endpoint, not a general "portal now persists things"
  expansion. Resist scope creep toward caching, additional endpoints, or write paths.
- Shared `flyway_schema_history` across two hosts (`admin`, `app`) booting against the same DB:
  Flyway migration application must stay idempotent regardless of which host boots first in a given
  environment — the boot tests above are the safety net, not just a nice-to-have.
- An anonymous endpoint is correct here only because `app` has zero security contour by construction
  (recon-confirmed) — this precedent does not automatically justify anonymous access for a
  *different* future `app` endpoint; that would need its own verification.
- Portal must not appear broken on a fresh database with zero PUBLISHED content — the 404-not-500
  contract on `readPublished` is what the frontend's graceful-degrade rendering depends on; do not
  let a future change turn a missing key into a 500.
