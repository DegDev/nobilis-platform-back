# Plan: Integrations screen (backend)

## Feature ID
NB-INTEG

## Scope
fullstack — backend part

**Paired plan:** `NB-INTEG-integrations-screen.md` in the frontend repo
(`/home/deg/www/nobilis-platform-front/.agent/plans/NB-INTEG-integrations-screen.md`).

## Applicable playbook
- `docs/playbooks/engine-screen-mounting.md` — backend-only mounting recipe (service auto-config
  gated on `EntityManagerFactory`, unconditional `@AutoConfigurationPackage`, controller auto-config +
  mandatory component-scan exclude on the host app). Recon-confirmed this is the pattern behind the
  existing `settings`/`roles`/`accounts` screens. **Applicability this pass:** likely *not* needed in
  full — see Architectural decisions §2 below; this feature rides the existing `Setting`
  service/controller rather than mounting a new store, so most of the recipe (new entity's
  auto-config, new service auto-config, component-scan exclude for a *new* controller) does not apply.
  Cited for context only; do not re-run the full mounting ritual for zero new infrastructure.

## Goal
Expose a prefix-filtered read path over existing `Setting` rows (`integration.*`) so the admin
Integrations screen can list and edit third-party API credentials, with zero new schema and zero new
permission — reusing `Setting`'s existing encrypt/mask/write-only behavior as-is.

## Architectural decisions

Locked (operator-ratified, precedent-backed — do not re-litigate):

1. **Reuse `Setting`, no new entity/migration.** Recon-confirmed the full existing contract:
   - `common/src/main/java/io/github/degdev/engine/common/settings/Setting.java:47-59` — `@Entity`,
     fields `key` (unique), `value` (`length = 4096`), `secret` boolean.
   - `.../settings/SettingsService.java:100-115` (`set`) — encrypts via `cryptoService.encrypt(value)`
     when `secret`; `:55-64` (`get`) — decrypts via `cryptoService.decrypt(...)` when `secret`. List
     paths (`:74-89`) never decrypt.
   - `admin/src/main/java/io/github/degdev/engine/admin/settings/SettingDto.java:42-45` — `from(...)`
     nulls the value when `secret` is true. This is the mask the Integrations read path must go
     through — never bypass it with a decrypt-all.
   - `common/.../crypto/CryptoService.java:80-86,97-109` — encrypt/decrypt, gated by
     `CryptoAutoConfiguration` `@ConditionalOnProperty(prefix = "nobilis.crypto", name = "master-key")`
     (`CryptoAutoConfiguration.java:33`).
2. **Key convention `integration.<provider>.<name>`**, one row per provider secret by default. No
   multi-key infrastructure, no provider enum/registry in the engine (engine/domain boundary — see
   CLAUDE.md).
3. **Endpoint shape — extend the existing settings list endpoint, do not mount a new store.** Smaller
   diff, reuses everything: add an optional `keyPrefix` query param to the existing
   `GET /admin/api/settings` list endpoint
   (`admin/.../settings/SettingsController.java:80-83`), backed by a new repository method.
   - `.../settings/SettingRepository.java:22-31` currently has exactly one method,
     `findByKey(String key)` — **recon-confirmed no prefix query exists yet.** Add
     `findByKeyStartingWith(String prefix, Pageable pageable)` (Spring Data derived query — no raw
     SQL/JPQL string concatenation, parameterized by the derived-query mechanism itself).
   - `SettingsController`'s list handler gains `@RequestParam(required = false) String keyPrefix`,
     passed through to the service, which picks `findByKeyStartingWith` vs. `findAll` based on
     presence. Response DTO unchanged (`SettingDto`, already masked).
   - Writes (`POST`/`PUT /{key}`) are reused as-is — the Integrations screen writes
     `integration.<provider>.<name>` keys through the same existing endpoints, no new write path.
   - Rejected alternative: a dedicated `/admin/api/integrations` controller — more surface (new
     controller, new DTO, new component-scan-exclude wiring per `engine-screen-mounting.md`) for the
     same underlying data; not justified when the existing endpoint only needs one query param.
4. **Module/package placement — do NOT touch the `integration` module.** Recon-confirmed
   `integration/src/main/java/io/github/degdev/engine/integration/` is an unrelated, pre-existing
   headless worker module (Kafka bus, notification dispatcher, scheduler — no HTTP endpoint,
   `package-info.java:16-19`). The new `keyPrefix` param and repository method belong in the existing
   `common/.../settings/` and `admin/.../settings/` packages, extending in place. No new module, no
   name collision.
5. **Permission — reuse `SETTINGS_MANAGE`, no new constant.** Recon-confirmed
   `auth/src/main/java/io/github/degdev/engine/auth/role/EnginePermissions.java:33` —
   `SETTINGS_MANAGE`. `SettingsController` already carries class-level
   `@RequiresPermission(EnginePermissions.SETTINGS_MANAGE)` (`SettingsController.java:60`) — extending
   the same controller means both list (read) and write inherit this gate automatically. No new
   permission is introduced.
6. **Masked tail — dropped.** Per the plan's own fallback rule: showing even a short decrypted suffix
   would require returning decrypted material to the client, which breaks the write-only guarantee.
   Locked: the read path returns only `SettingDto` as it exists today (`value` null when `secret`) — the
   frontend renders "set / not set" from `value == null && secret == true` vs. a real value, nothing
   more granular. No DTO change needed for this.

## Files to create / change

Module: `common` (repository), `admin` (controller + DTO wiring, if any DTO change is needed —
none anticipated since `SettingDto` is reused unchanged).

- `common/src/main/java/io/github/degdev/engine/common/settings/SettingRepository.java` — add
  `Page<Setting> findByKeyStartingWith(String prefix, Pageable pageable)` (Spring Data derived query
  method) alongside the existing `findByKey`.
- `common/src/main/java/io/github/degdev/engine/common/settings/SettingsService.java` — add a
  list-by-prefix method (or extend the existing list method with an optional prefix argument) that
  calls the new repository method; must go through the same masking construction as the current list
  path (no decrypt).
- `admin/src/main/java/io/github/degdev/engine/admin/settings/SettingsController.java` — add
  `@RequestParam(required = false) String keyPrefix` to the existing `GET` list handler
  (`:80-83`), pass through to the service.
- No changes to `Setting.java`, `SettingDto.java`, `CryptoService.java`, `EnginePermissions.java`, or
  any Liquibase/Flyway migration — confirmed zero schema/permission delta.

## Open questions

None outstanding for this pass — the endpoint-shape and masked-tail forks are locked above (§3, §6)
per the original prompt's instruction to pick the smaller diff and to drop the tail rather than weaken
write-only. If the frontend build pass finds the `keyPrefix` query-param shape awkward once wired end
to end, revisit — but no blocking ambiguity today.

## Testing strategy

### Backend
- Unit test on `SettingRepository`/`SettingsService`: `findByKeyStartingWith("integration.")` returns
  only matching rows (data-seeded test, e.g. `@DataJpaTest`).
- Integration test on `SettingsController`: a request with `keyPrefix=integration.` against a mix of
  `integration.*` and non-`integration.*` seeded settings returns only the matching subset.
- **Security-focused test (DoD-mandated):** a `secret=true` Setting under `integration.*` is fetched
  via the prefix-listing path and its `value` in the response is asserted `null` — proves the read
  path never leaks decrypted secret material through the new query param. This is the one test this
  slice cannot skip.
- Existing `SettingsControllerTest`/`SettingsServiceTest` (if present) must stay green — no behavior
  change to the no-prefix-param case.

## Related features
- Cross-repo: paired frontend plan `NB-INTEG-integrations-screen.md` in `nobilis-platform-front` —
  the frontend `integrations-api.ts` is written against this endpoint's final shape
  (`GET /admin/api/settings?keyPrefix=integration.`).
- Dependencies: none blocking — rides entirely on the existing `settings` module from earlier
  milestones.

## Risks
- **Query-param naming stability.** `keyPrefix` is chosen now; if a different admin screen later needs
  a similar filter with a different convention, reconcile then — not a reason to over-generalize this
  param now (Simplicity First).
- **Derived-query correctness.** `findByKeyStartingWith` must be verified against Spring Data's actual
  `LIKE 'prefix%'` translation (parameterized, no injection risk) — cover with the `@DataJpaTest`
  above rather than assuming.
- **Scope creep temptation:** a dedicated `/admin/api/integrations` controller "for clarity" was
  explicitly rejected (§3) — resist re-introducing it mid-build without a concrete reason surfacing
  during implementation.
