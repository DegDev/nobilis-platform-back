# Plan: CMS content-block mechanism — backend

## Feature ID
NB-CMS

## Scope
fullstack — backend part

**Paired plan:** `NB-CMS-cms-content-blocks.md` in the `nobilis-platform-front` repo.

## Branch
`03-cms-screen` — cut in the frontend repo already (from fresh `origin/main`, during recon). This
backend repo is currently still on `main`. **Open action, not done by this plan step:** cut a
matching local branch here — `git fetch origin && git switch -c 03-cms-screen origin/main` — before
BUILD starts, so both repos land the vertical slice on the same branch name (branch discipline: one
branch per milestone, no slash, upstream tracks the same-named `origin/<branch>`).

## Applicable playbook
- `docs/playbooks/engine-screen-mounting.md` ([ready]) — this is exactly the "new engine admin
  screen backed by a JPA store" task class the playbook was extracted for (settings/roles/accounts).
  Follow its 8-point mounting checklist verbatim for the CMS service + controller; do not invent a
  different wiring.
- `docs/playbooks/lombok-conventions.md` ([ready]) — apply when writing the new entity/DTO/service
  classes (`ContentBlock`, `ContentTranslation`, `ContentBlockDto`, `ContentBlockService`).
- fullstack: paired frontend plan references the settings screen directly (no ready frontend
  playbook for the CRUD-screen class yet).

## Goal
Add a generic, localized content-block storage + admin API mechanism (`key` + `status` + per-locale
translations) to the engine, following the settings/roles/accounts mounting pattern exactly, so a
domain repo can later key arbitrary content into it — without modeling any concrete content
(news/banners are milestone 07) and without wiring image storage (deferred, tracked as BL-002).

## Architectural decisions

(Locked by the operator after RECON on branch `03-cms-screen`; recon confirmed no CMS model existed
anywhere before this plan — see recon report in the front repo's conversation history / this plan's
provenance. Not re-litigated here.)

1. **Granularity = generic content-block mechanism, not typed entities.** No `NewsItem`/`Banner`/
   `LandingText` tables. One generic shape: a stable string `key` (unique) + a `status`
   (DRAFT/PUBLISHED) + a set of localized `ContentTranslation` rows. Domain repos supply concrete
   keys and render the resolved value at their own layer — putting concrete content types in this
   engine repo would violate the engine/domain boundary (`CLAUDE.md` §"Boundary: engine vs domain").
   Same philosophy as `settings` (key/value mechanism), extended with localization.

2. **i18n-content shape = child translation table** — the first justified `@OneToMany` in this
   backend (recon: zero existed before this feature). Schema:
   - `content_block(id, key UNIQUE NOT NULL, status NOT NULL, created_at, updated_at, version)`
   - `content_translation(id, content_block_id FK NOT NULL, locale NOT NULL, body NOT NULL,
     created_at, updated_at, version, UNIQUE(content_block_id, locale))`

   Rejected alternatives (recon-surfaced, operator-ratified): JSON-per-locale column (loses FK
   integrity + indexing), duplicate-row-per-locale on a flat table (breaks "one item = one row",
   tangles draft/publish semantics with locale). Locale values reuse the `01-common` contract:
   `LocaleResolver` (`common/.../i18n/LocaleResolver.java:36-53`) — `ru`/`ro`, `DEFAULT_LOCALE = ru`,
   fallback requested-locale → `ru` when a translation row is missing. This is also the FIRST real
   consumer of `LocaleResolver` outside its own module — recon found it defined but never wired to
   an HTTP layer before this feature.

3. **Images/MinIO deferred for v1 (BL-002).** Text content only. MinIO exists today only as an
   unwired docker-compose service (`stack.yml:77-93`) — zero SDK dependency, zero Java client, zero
   consumer anywhere in either repo (recon-confirmed). Wiring an S3/MinIO SDK into the first CMS
   pass would be infrastructure-ahead-of-need. **Action required in this pass:** add a
   `docs/architecture-backlog.md` entry `BL-002` recording the deferral (entity/text model ships now,
   image attachment is a separate later pass) so it isn't silently lost — mirrors how `BL-001`
   (speculative infrastructure) is already tracked there.

4. **Draft/publish semantics**: `status` lives on `content_block` (per-item, not per-translation) —
   a block is published as a whole; a missing translation at read time falls back to `ru`, it does
   not gate publish state. No scheduling, no versioning beyond the standard optimistic-lock
   `version` column, no draft-per-locale. Extract-don't-predict: build only what this pass needs.

5. **Mounting — copy `engine-screen-mounting.md`'s three-part shape exactly**, with these
   CMS-specific parameters:
   - **Service** (`common` module): `ContentBlockService`, an `@AutoConfiguration`-contributed
     `@Bean` gated `@ConditionalOnBean(EntityManagerFactory.class)` (no `CryptoService` — content is
     not encrypted, unlike settings' secret-value path), ordered `@AutoConfiguration(after =
     HibernateJpaAutoConfiguration.class)`. Repository (`ContentBlockRepository`) injected into the
     `@Bean` method, never used as the gate (playbook §1, the timing trap that recurred 3×).
   - **Persistence package registration** stays unconditional — reuse `common`'s existing
     `@AutoConfigurationPackage` (it already covers `io.github.degdev.engine.common`, matching the
     "combined shape" `SettingsAutoConfiguration` uses, per playbook §2) — no new package-scan
     registration needed if the new entity/repo land under the same base package as settings.
   - **Controller** (`admin` module): `ContentBlockController` under `/admin/api/content-blocks`,
     registered via a host `@AutoConfiguration` (`ContentBlockWebAutoConfiguration`) gated
     `@ConditionalOnBean(ContentBlockService.class)`, ordered `after` the service auto-config
     (playbook §3).
   - **Scan-exclude (mandatory, not optional — the trap that bit accounts):** add
     `ContentBlockController` to `AdminApplication`'s `@ComponentScan`
     `@Filter(type = ASSIGNABLE_TYPE, ...)` exclude list alongside `SettingsController`,
     `RoleController`, `AccountController` (`AdminApplication.java:59-60`).
   - **Lazy collection**: `content_block.translations` is a lazy `@OneToMany` — force-initialize
     inside `ContentBlockService`'s `@Transactional` methods before returning a DTO (`.size()` or
     equivalent), exactly as `RoleService.java:160` and `AccountService.java:126,129,131` do, or
     `LazyInitializationException` fires under `open-in-view=false`. This is the SAME trap, now
     doubled by the translations collection.
   - **DTO placement**: since the service must build the DTO in-transaction (lazy collection +
     locale resolution), the DTO (`ContentBlockDto`, `ContentTranslationDto`) lives in `common`
     (the service's module) — never returned as a bare entity for the controller to map after the
     transaction closes (playbook's "DTO lives on the side that owns the read" rule).
   - **Error mapping**: unknown-locale, duplicate-key (unique constraint on `content_block.key`),
     and not-found all map to `ProblemDetail` (RFC 9457) via `admin`'s existing
     `GlobalExceptionHandler`, the same contract roles/accounts/settings already use.

## Files to create / change

### Backend

Modules: `common`, `admin`.

- `common/src/main/java/io/github/degdev/engine/common/cms/ContentBlock.java` — `@Entity`, fields
  per schema above.
- `common/src/main/java/io/github/degdev/engine/common/cms/ContentTranslation.java` — `@Entity`,
  child side of the `@OneToMany`.
- `common/src/main/java/io/github/degdev/engine/common/cms/ContentBlockRepository.java` — Spring
  Data JPA repository, `findByKey`, paged `findAll`.
- `common/src/main/java/io/github/degdev/engine/common/cms/ContentBlockService.java` — plain class
  (not `@Service`, matching `SettingsService`'s shape), CRUD + locale-resolve-with-fallback method.
- `common/src/main/java/io/github/degdev/engine/common/cms/ContentBlockDto.java`,
  `ContentTranslationDto.java` — read-model DTOs, built in-transaction.
- `common/src/main/java/io/github/degdev/engine/common/cms/ContentBlockAutoConfiguration.java` —
  service `@Bean`, `@ConditionalOnBean(EntityManagerFactory.class)`, `after
  HibernateJpaAutoConfiguration`.
- `common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `ContentBlockAutoConfiguration`.
- `common/src/main/resources/db/migration/V2__cms_content.sql` — creates `content_block` and
  `content_translation` tables per the schema in Architectural decisions §2. (`common`'s Flyway
  history is currently at `V1__baseline.sql` only, per-module numbering confirmed by recon — this is
  the next number in `common`'s own chain, not a global counter.)
- `admin/src/main/java/io/github/degdev/engine/admin/cms/ContentBlockController.java` —
  `@RestController @RequestMapping("/admin/api/content-blocks")`, list/get/create/update/delete,
  `?locale=` query param support on read endpoints.
- `admin/src/main/java/io/github/degdev/engine/admin/cms/ContentBlockCreateRequest.java`,
  `ContentBlockUpdateRequest.java` — request DTOs (key, status, translations map/list).
- `admin/src/main/java/io/github/degdev/engine/admin/cms/ContentBlockWebAutoConfiguration.java` —
  controller `@Bean`, `@ConditionalOnBean(ContentBlockService.class)`, `after`
  `ContentBlockAutoConfiguration`.
- `admin/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `ContentBlockWebAutoConfiguration`.
- `admin/src/main/java/io/github/degdev/engine/admin/AdminApplication.java` — add
  `ContentBlockController` to the `@ComponentScan` `ASSIGNABLE_TYPE` exclude list (mandatory, see
  Architectural decisions §5).
- `admin/src/main/java/.../GlobalExceptionHandler.java` (existing) — add mappings if CMS introduces
  new exception types (duplicate key, unknown locale) beyond what roles/accounts already cover.
- `docs/architecture-backlog.md` — add `BL-002` entry recording the images/MinIO deferral (per
  Architectural decisions §3).

## Open questions
1. Confirm the exact base package `@AutoConfigurationPackage` used by
   `SettingsAutoConfiguration.java:63` covers the new `cms` sub-package without changes — read the
   file at BUILD time before assuming (plan states "if land under the same base package", not yet
   verified line-by-line for this specific sub-package).
2. `?locale=` on read endpoints: single-locale response (resolved value only) vs. all translations
   returned and the frontend picks — recon shows `LocaleResolver` is designed for server-side
   resolution (single value out), but the admin SCREEN needs to edit both locales at once (list all
   translations). Likely: list/detail-for-admin-editing returns ALL translations; a separate
   locale-resolved read (for eventual domain/public consumption, out of scope this pass per app-host
   -boot decision) would return one. Confirm shape at BUILD time — not locked here.
3. Whether `ContentBlockDto`/`ContentTranslationDto` need a dedicated request/response asymmetry
   (like `SettingDto` masking secret values) — CMS content isn't secret, likely no masking needed;
   confirm no false-positive concern before BUILD.

## Testing strategy

### Backend
- Unit tests (JUnit 5): `ContentBlockService` — CRUD, locale-fallback-to-ru resolution, duplicate-key
  rejection, force-init of the lazy translations collection.
- Integration tests (Testcontainers PG18):
  - `CmsCrudIntegrationTest` — full round-trip: create a block with ru+ro translations, `GET
    ?locale=ro` returns ro, `GET ?locale=xx-nonexistent-translation` falls back to ru, a DRAFT block
    is excluded from any public/published-only read path but visible via the admin list, delete
    removes the block and cascades its translations.
  - `AdminApplicationTest` — extend the stateless-host assertions:
    `doesNotMountCmsApiWithoutAStore` (new test method alongside the existing settings/roles/accounts
    ones), proving `ContentBlockService`/`ContentBlockController` beans are absent with no
    datasource, matching `AdminApplicationTest.java:99,108`'s existing pattern.

## Related features
- Cross-repo: paired frontend plan `NB-CMS-cms-content-blocks.md` in `nobilis-platform-front`
  (the admin screen consuming this contract).
- Depends on: `01-common` (`LocaleResolver`, `?locale=` contract — first real consumer here),
  `03-app-admin-shell` settings/roles/accounts (the mounting pattern being copied).
- Blocks: any domain content (milestone 07) that would key into this mechanism; app-portal landing
  consuming real content (separate pass, per the app-host-boot decoupling decision,
  `docs/sources-log.md:84`).

## Risks
- The lazy-collection-plus-locale-resolution combination is a compounded version of a trap that has
  already bitten roles/accounts individually — extra care needed force-initializing
  `translations` AND doing locale-fallback logic inside the same `@Transactional` boundary.
- `content_block.key` uniqueness + concurrent creation could produce a race on the DB unique
  constraint — surface as a `ProblemDetail` 409, not a 500; confirm `GlobalExceptionHandler` already
  has a generic `DataIntegrityViolationException` mapping before assuming new code is needed.
- Frontend repo is ahead on branch (`03-cms-screen` already cut there); this repo needs the matching
  branch cut before BUILD starts here, or the two halves land on mismatched branches.
