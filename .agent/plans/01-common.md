# Plan: 01-common — shared backend foundation

## Feature ID
`01-common` (milestone; depends on `00-foundation`, done)

## Scope
Fullstack — **backend part**. Engine `common` module (library jar, not runnable). Zero domain logic.

**Paired plan:** `01-common.md` in the frontend repo (`nobilis-platform-front`) — covers task 7
(front-common: signal-based locale service + typed HTTP wrapper skeleton).

## Applicable playbook
- `docs/playbooks/` — **none fits yet**. The folder is intentionally empty until the first real
  example (CRUD screen / fullstack slice at milestone `03`/`07`); per its README, playbooks are
  extracted after the first example, not written ahead on guesses. No recipe to follow here.

## Goal
A shared `common` library that every other module builds on: a persistence base, an
encrypted-settings facility, and locale resolution — all verifiable, with no domain logic.

## Architectural decisions (recon — record each in `docs/sources-log.md`)

- **ID strategy → `Long` + `GenerationType.IDENTITY` on `BaseEntity`.** Single-tenant /
  single-Postgres / modular monolith rules out the distributed scenarios where UUID wins. The one
  real UUID benefit (non-enumerable public IDs) is the concern of exactly one outward-facing entity
  (`Order`) and is solved at `07` by a separate UUIDv7 public reference *alongside* the `Long` PK on
  that entity only — not engine-wide. Keep-it-simple + extract-don't-predict. UUIDv7 considered and
  rejected as the engine default.

- **Migrations → Flyway (SQL-first), not Liquibase.** Postgres is nailed; use its specifics rather
  than abstract the DB. Liquibase's changeset DB-abstraction is wasted on a single-DB engine; Flyway
  runs raw SQL directly.

- **Audit → timestamps now, user-audit deferred to `02`.** `created_at` / `updated_at` via JPA
  auditing (`@CreatedDate` / `@LastModifiedDate` + `AuditingEntityListener`) in `01`. `created_by` /
  `updated_by` deferred to `02-auth`: no principal exists yet, so `AuditorAware` returns a
  system/empty value for now.

- **Crypto → AES-256-GCM, direct (no envelope), explicit decrypt at point of use.** Storage format
  `v1:Base64(IV[12] ‖ ciphertext+tag)` — single string, single column. 96-bit IV, fresh random per
  encryption (`SecureRandom`); 128-bit GCM tag appended by JCA. The `v1:` prefix enables future
  algorithm/key rotation. Decryption is an explicit `CryptoService.decrypt()` call at the use site
  (bank / Telegram / SMS), **not** a transparent JPA converter — keeps plaintext secrets out of
  entity memory except exactly when needed. Master key from env/secret via property placeholder
  (NEVER committed); a CLI command generates a fresh 256-bit key. Sources: NIST SP 800-38D (unique
  IV per key), OWASP Cryptographic Storage Cheat Sheet, Java JCA `AES/GCM/NoPadding`.

- **i18n → default locale RU, fallback chain `requested → RU`.** Locales RU + RO. Spring
  `MessageSource` for backend static UI strings + a `LocaleResolver` (request header/param →
  fallback RU; user-preference wires in later). In `01` only the resolver + fallback + bundle
  scaffold; real RU/RO bundles are populated at `05-i18n-static`.

## Contract (back ↔ front) — locale transport

The seam between the front locale service and the backend `LocaleResolver`. Both sides implement
to THIS contract, not to each other's code.

- **Transport: query parameter `?locale=<code>`** on API requests. (Chosen over `Accept-Language`
  for explicitness and easy manual testing — the locale is visible in the URL.)
- **Valid values:** `ru`, `ro` (lowercase ISO 639-1). Anything else is treated as absent.
- **Resolution / fallback:** `LocaleResolver` reads `?locale=`. If absent, empty, or not in
  {`ru`,`ro`} → fall back to the default locale **`ru`**. No error on bad input — silently default.
- **Default locale:** `ru` (single source of truth: a `DEFAULT_LOCALE` constant in `common`,
  mirrored by the front locale service's default).
- **Scope in `01`:** the resolver + fallback only. No per-endpoint enforcement, no message bundles
  populated yet (that's `05`). The contract is what `02`/`03`/`05` build against.
- **Front side (paired plan):** the front sends `?locale=<currentLocale>` on API calls via the HTTP
  wrapper; `currentLocale` signal defaults to `ru`, switchable to `ro`. Wiring of the actual query
  param onto requests lands when the first real call appears (`02`/`03`) — in `01` the front only
  holds the signal + default.

## Files to create / change

### Backend — module `common`

- **Persistence base**
  - `common/src/main/java/.../entity/BaseEntity.java` — `@MappedSuperclass`: `Long id`
    (`@Id`, `@GeneratedValue(strategy = IDENTITY)`), `@Version` optimistic-lock field,
    `createdAt`/`updatedAt` (`@CreatedDate`/`@LastModifiedDate`, `@EntityListeners(AuditingEntityListener.class)`).
  - `common/src/main/java/.../config/JpaAuditingConfig.java` — `@EnableJpaAuditing`.
  - `common/src/main/java/.../config/SystemAuditorAware.java` — `AuditorAware<String>` system stub
    (returns a system/empty value; real user wiring at `02`).
- **Flyway**
  - build deps: `flyway-core` + `flyway-database-postgresql`.
  - `common/src/main/resources/db/migration/V1__baseline.sql` — creates the `setting` table.
  - config so Flyway runs against the Postgres instance from `stack.yml`.
- **Repository layer**
  - package layout / Spring Data JPA conventions. Introduce `@NoRepositoryBean BaseRepository<T
    extends BaseEntity>` **only if** a shared method is actually needed now — default: skip, use
    plain `JpaRepository` per entity (decide at task 3).
- **Crypto**
  - `.../crypto/GcmCipher.java` — AES-256-GCM primitive: `encrypt(byte[]) → byte[]` (prepends fresh
    12-byte IV), `decrypt(byte[]) → byte[]`.
  - `.../crypto/CryptoService.java` — String API: `encrypt(String) → "v1:" + Base64(IV‖ct+tag)`,
    `decrypt(String)` (parse prefix, reject unknown versions).
  - master key from `nobilis.crypto.master-key` (env-backed placeholder); fail fast if absent in a
    profile that needs it.
  - key-generation CLI command (guarded `ApplicationRunner` / dedicated entrypoint) printing a fresh
    Base64 256-bit key for the operator.
- **Settings**
  - `.../settings/Setting.java` — `BaseEntity` + unique `key`, `value` text, `secret` boolean.
  - `.../settings/SettingRepository.java` (`JpaRepository`).
  - `.../settings/SettingsService.java` — `get`/`set`; encrypt-on-write, decrypt-secret-on-read via
    `CryptoService`.
- **i18n**
  - `.../config/I18nConfig.java` — `MessageSource` (`ReloadableResourceBundleMessageSource`) +
    `LocaleResolver` (resolve request locale, fall back to RU).
  - `common/src/main/resources/messages.properties` (default) + empty `messages_ru.properties` /
    `messages_ro.properties` placeholders (real bundles at `05`).

### Secrets / hygiene
- Master key never in a committed file — env/secret only; the property file holding it (or local
  override) under `.gitignore`.

## Tasks (atomic — one pass each)

1. **Persistence base** — `BaseEntity`, JPA auditing config, `AuditorAware` system stub.
2. **Flyway wiring + baseline** — deps + config; `V1__baseline.sql` (`setting` table); verify it
   applies to Postgres from `stack.yml`.
3. **Repository layer** — conventions / package layout; `BaseRepository` only if a shared method is
   needed now (default: skip).
4. **Crypto** — `GcmCipher`, `CryptoService` (`v1:` prefix, parse/guard), master-key env config,
   key-generation CLI command.
5. **Settings** — `Setting` + `SettingRepository` + `SettingsService` (encrypt-write /
   decrypt-secret-read).
6. **i18n resolution** — `MessageSource` + `LocaleResolver` (fallback RU) + bundle scaffold.
7. **front-common (minimal)** — *in the frontend repo; see paired plan.*
8. **Tests** — crypto round-trip + GCM tamper; locale fallback; settings secret round-trip.

## Open questions
1. `BaseRepository` shared-method need — decide at task 3 (default: skip, plain `JpaRepository`).
2. `@Version` on every entity vs opt-in — default: on `BaseEntity` (cheap, prevents lost updates);
   revisit if a high-churn entity needs otherwise.

## Testing strategy

### Backend (JUnit 5)
- **Unit:** crypto round-trip (encrypt→decrypt); GCM tamper-detection (mutated ciphertext →
  authentication failure, not silently wrong); locale fallback (missing key → RU default); settings
  secret round-trip (stored ciphertext, read plaintext).
- **Integration (Testcontainers or `stack.yml` Postgres):** Flyway `V1` baseline applies; `Setting`
  persists with a `v1:`-prefixed ciphertext in the column for secret values.

## DoD (verifiable)
- `common` builds as a jar (not runnable); `ai` / `auth` / `app` / `admin` / `integration` resolve it.
- Flyway applies `V1` to the Postgres from `stack.yml`; the `setting` table exists.
- Crypto round-trip green; a mutated ciphertext fails GCM authentication.
- Locale fallback covered by a test (missing key → RU default).
- A secret setting is stored encrypted (`v1:` prefix visible in the column) and read back decrypted.
- Zero domain logic in `common`.
- Each crypto/i18n/ID/migration decision recorded in `docs/sources-log.md` (NIST/OWASP/JCA links).

## Related features
- **Paired plan:** `01-common.md` in `nobilis-platform-front` (task 7, front-common).
- Feeds `02-auth` (token/identity build on `common`), `03` (CRUD framework + settings screen), `04`
  (notification dispatcher uses locale resolution), `06` (`ai` reads keys from settings), `07`
  (`Order` adds a UUIDv7 public reference alongside its `Long` PK).

## Risks
- Crypto correctness is the one place to get exactly right (IV uniqueness, tag handling) — covered by
  the tamper test in task 8.
- Master-key handling must never land in a committed file — env/secret only; properties gitignored.
