# Code Conventions — nobilis-platform

The documentary layer of conventions (what the formatter/linter can't catch). Machine layer:
Spotless + google-java-format + Checkstyle (back), Prettier + angular-eslint (front), gated in CI
on every PR/push. Release is a manual `mvn deploy`, not an auto-deploy on merge.

Principle: **style is a nail in the build, not discipline or memory.** Tooling normalizes output
regardless of who/what wrote the code.

## Backend (Java)

**Standard:** Google Java Style. Formatter — Spotless + google-java-format. (Alternative — Palantir
Java Format, if Spring fluent/builder chains read better at 4-space.)

**Linter:** Checkstyle, a light ruleset for what the formatter doesn't catch:
- naming,
- no wildcard imports,
- import/member ordering.

**Naming (rock-solid):**
- Types (class/record/enum/interface) — `PascalCase`.
- Methods/fields/variables/parameters — `camelCase`.
- `static final` constants — `UPPER_SNAKE_CASE`.
- Packages — lowercase, reverse-domain: `io.github.degdev.engine.<module>.<feature>`.
- No Hungarian notation, no `m_`, no `I`-prefix on interfaces.

**Structure:**
- package-by-feature (not package-by-layer).
- Constructor injection (no field `@Autowired`).
- Clear DTO ↔ entity boundaries: an entity does not leak beyond its layer.
- Error handling — a single approach (domain exceptions + handler), no swallowing.

**Database table naming:**
- Convention: `entity_extra_fields` — entity name FIRST, so related tables sort together
  when listing the schema (e.g. `account`, `account_identity`, `account_realm`, `account_role`
  group under `account`; `role`, `role_permission` group under `role`).
- Avoid SQL reserved words as table names (`user`, `order`, etc.) — they force quoting in raw
  SQL. Prefer a non-reserved synonym (`account` instead of `user`).
- Column types: use `varchar` for enum-like values; do NOT use native Postgres `CREATE TYPE
  AS ENUM` (values can't be dropped/renamed without recreating the type — migration pain).
  "Enum-ness" lives in Java (`@Enumerated(STRING)`) or in string-constant catalogs, not in the DB.
- Join tables: composite PK (`PK(a_id, b_id)`), no surrogate id, unless the link is itself an
  audited entity (then it extends BaseEntity with its own id).
- Unique constraints named `uq_<table>_<col>` (existing convention from V1 baseline).

**Database migrations (Flyway):**
- Flyway, SQL-first, forward-only.
- Versions are GLOBAL across the classpath — when a host depends on multiple modules (e.g. admin
  depends on both `common` and `auth`), all of their `db/migration` folders merge into ONE version
  history. A module cannot see another module's next free number.
- All migrations: `VYYYYMMDDHHMMSS__snake_desc.sql` (14-digit UTC timestamp), not sequential
  `V<n>`. The global namespace makes sequential numbers collide across modules; a timestamp makes
  that collision structurally impossible and self-evidently encodes apply order, with no central
  number coordination needed. `V1__baseline.sql` is the sole permanent exception — the
  traditional baseline name, never renamed.
- Never rename an already-applied migration on a live database — Flyway validates by checksum,
  and a rename with no matching `flyway_schema_history` repair breaks every environment where it
  ran. A rename is only safe against a database with no applied history for that migration (fresh/
  reset). The former `auth` `V2`..`V4` sequential names were cutover to the timestamp format under
  exactly that condition (NB-MIGRATIONS: moved into `common`, dev DB reset first) — not a
  precedent for renaming applied migrations elsewhere.

## Frontend (Angular)

**Standard:** the official Angular Style Guide. Formatter — Prettier. Linter — angular-eslint
+ `eslint-config-prettier` (so rules don't conflict).

> ⚠️ Angular 22 is fresh (released June 3, 2026): some style details shifted (e.g. the `.component`
> suffix). Before finalizing this section — verify against the live Angular Style Guide, not memory.

## Common

- **EditorConfig** in both repos: charset, EOL, indent, trailing-whitespace. Read by all IDEs.
- **Pin formatter versions** — otherwise format drifts between machines and CI.
- Tests — next to the feature; unified test-naming conventions (to be settled with the first module).

## Commit messages

Subject and body are visually separated — never one dense brick.

- **Subject:** imperative, ≤72 chars, Conventional-Commits type + scope (`feat(scope): …`,
  `fix(scope): …`, `docs(scope): …`, `chore(scope): …`, `refactor(scope): …`). No trailing period.
- **One blank line** between subject and body — mandatory.
- **Body: default to bullet points (`- `), one per discrete change.** A commit that touches more
  than one thing MUST use bullets, not a prose paragraph. Reason: Markdown renderers (GitHub PR
  descriptions especially) collapse single newlines inside a paragraph into one running line — a
  wrapped prose paragraph that looks fine in `git log` renders as a solid brick on GitHub. Bullets
  survive both. A single-sentence body may stay prose; anything with 2+ points is bullets.
- Wrap body lines at roughly 72 chars.
- **Blank line** between distinct paragraphs/sections in the body.
- **Optional trailing metadata** (refs, co-authored-by) after a blank line, at the end.
- Factual — what changed and why, not a changelog dump.

Applies to every commit in this repo, including a proposed message an agent produces at the
commit-gate (see `CLAUDE.md`).

## Why public standards, not "how previous projects did it"

Adopting a named public standard (Google Java Style, Angular Style Guide) is a sources-log for code
style: "followed Google Style", not "copied someone else's conventions". Cleaner for clean-room and
clearer to any external contributor.
