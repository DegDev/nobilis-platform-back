# Plan: 03-app-admin-shell — admin host, CRUD framework, admin screens (backend)

> **RECONSTRUCTED POST-HOC (2026-07-09).** Built without running `/plan` first. Reconstructed from
> the git history (`03-app-admin-shell` commits `6ceb6dc`…`6132630`, PRs #3–#5) and the
> `03-app-admin-shell` rows of `docs/sources-log.md` — the actual source of truth. **Recon** and
> **Open questions** are not genuine pre-code sections (marked N/A / final state). Goal, Decisions,
> Spec, Tasks, DoD are factual as-built. NOTE: this milestone is **admin-track only**; the app-host
> and remaining `03` screens (CMS, notifications-config, integrations) are still open (see the
> build plan) — the app-host boot fix landed separately (`app-host-boot`).

## Feature ID
`03-app-admin-shell` (milestone; depends on `01-common`, `02-auth`, done)

## Scope
Fullstack — **backend part**. Runnable `admin` host + the admin CRUD framework + settings/roles/
accounts admin APIs, all mounted opt-in.

**Paired plan:** `03-app-admin-shell.md` in the frontend repo (`nobilis-platform-front`) — the
Angular `admin` app (accounts / roles / settings / dashboard / auth screens, PrimeNG).

## Applicable playbook
- `docs/playbooks/engine-screen-mounting.md` — extracted DURING this milestone (`96a8832`,
  `[ready]`); it captures the very pattern this milestone established (conditional-`@Bean`
  auto-config mounting + the `@RestController` scan-exclude).
- `docs/playbooks/lombok-conventions.md` — followed for every entity/DTO/service.

## Recon (before code)
**N/A — reconstructed post-hoc.** The premises a real recon would have surfaced (Boot-4 auto-config
package moves, the `@ConditionalOnBean(EntityManagerFactory)` timing, the `@RestController`-is-a-
`@Component` scan clash) are recorded as resolved facts under Decisions / Architectural decisions.

## Decisions (as-built, from sources-log)
1. **Stateless admin host** — exclude the four DB auto-configs so it boots with no Postgres; DB
   returns profile-gated by properties, not code.
2. **The contour is host policy** — `AdminContourFilter` (401 anon / 403 wrong-realm) runs after the
   auth gate; the gate stays a never-rejecting mechanism.
3. **`common` mounts via auto-configuration** — closes the sibling-package scan gap; auditing gated
   on the `EntityManagerFactory` bean, NOT `@ConditionalOnClass`.
4. **A DB-only screen mounts only with its store** — conditional `@Bean` controller +
   `@RestController` kept OUT of component scan (the single-registration rule).
5. **Error contract = RFC 9457 ProblemDetail**; two-layer authz (servlet realm gate + MVC
   `@RequiresPermission` interceptor).
6. **Accounts = manage-existing only** — no create, no hard delete (soft = status→BLOCKED); DB-login
   deferred to a later milestone.

## Goal
Stand a real, secured, runnable `admin` host with a reusable CRUD framework, then deliver the first
three admin capabilities (settings, roles, accounts) on it — each mounted only when its store
exists, so the same host boots stateless or DB-backed by a one-line profile flip.

## Architectural decisions (as-built — full provenance in `docs/sources-log.md`)

- **Stateless host (pass 1):** `AdminApplication` excludes `DataSource`/`HibernateJpa`/
  `DataJpaRepositories`/`Flyway` auto-config (Boot-4 module-split package names, verified against the
  4.1 sources). Proven by `AdminApplicationTest` (boots with no `DataSource` bean).
- **Contour (pass 1):** `AdminContourFilter` + explicit `FilterRegistrationBean` ordering (gate at
  `LOWEST_PRECEDENCE-10`, contour at `LOWEST_PRECEDENCE`); guarded on `nobilis.auth.jwt.secret`.
- **Local secrets (pass 1):** real values only in a gitignored `application-local.properties`;
  committed `.example` has placeholders — runnable-owns-config.
- **`common` mounting (pass 2):** three auto-configs in common's `.imports` — `Crypto`
  (`@ConditionalOnProperty` master-key), `I18n` (unconditional, `before MessageSourceAutoConfiguration`,
  resolver renamed `engineLocaleResolver`), `Persistence` (auditing, `@ConditionalOnBean(EntityManagerFactory)`
  — NOT `@ConditionalOnClass`, because the JPA classes are always on the classpath).
- **DB-return profile-gated (pass 3a):** exclusions expressed as `spring.autoconfigure.exclude` in
  properties; a profile resets it to empty (Boot binds `""` → `[]`), zero code change.
- **Settings registration (pass 3a):** `@AutoConfigurationPackage` (additive) registers the entity/
  repo — NOT `@EntityScan`/`@EnableJpaRepositories` (both break `auth` globally). Register the module
  ROOT package (LinkedHashSet de-dup trap); service `@ConditionalOnBean({EntityManagerFactory, CryptoService})`
  ordered after BOTH.
- **CRUD framework (pass 3a):** RFC 9457 ProblemDetail error contract; two-layer authz — servlet
  contour (coarse) + `@RequiresPermission` `HandlerInterceptor` throwing `ForbiddenException` (fine).
- **Screen mounting (pass 3a):** `SettingsController` is a conditional `@Bean`
  (`@ConditionalOnBean(SettingsService)` in an auto-config — reliable, unlike on a scanned bean) AND
  excluded from `@ComponentScan` (a `@RestController` is a `@Component` → would double-register).
  This is the reusable pattern captured as the `engine-screen-mounting` playbook.
- **Roles (pass 4b):** placement split — `RoleService` in auth, web layer in admin. Service gated on
  the EMF (not the repository — `@ConditionalOnBean` timing lesson, 2nd application). Invariants:
  referenced-delete → 409, unknown permission → 400 (REJECT, not warn), lazy `permissions`
  force-initialized in-tx.
- **Accounts (pass 4d):** same split; the `@RestController` scan-exclude is the necessary THIRD mount
  site (the trap that fired). `AccountDto` built in-tx (two lazy collections + a separate identity
  fetch) and placed in auth (module direction `auth ← admin`). `secret_hash` structurally excluded
  from the DTO. Manage-existing only: no create, soft-delete = status→BLOCKED, roles by id,
  unknown-ref → 400, self-lockout guard deferred (config-admin has no account row).
- **V4 seed (pass 4a):** one engine-level `ADMIN` role carrying `EnginePermissions.ALL`, idempotent,
  code aligned with `AdminLoginService.ADMIN_ROLE`. Engine permissions only — domain roles at `07`.

## Spec — files created / changed (backend)

- **admin host:** `AdminApplication` (spelled-out `@SpringBootApplication` + scan-exclude filters),
  `application.properties` (exclusions + defaults), `application-local.properties.example`,
  `AdminContourFilter`, `ContourSecurityConfiguration`.
- **common mounting:** `CryptoAutoConfiguration`, `I18nAutoConfiguration`, `PersistenceAutoConfiguration`,
  `SettingsAutoConfiguration`, common's `.imports`.
- **CRUD framework (admin):** `GlobalExceptionHandler` (RFC 9457), `@RequiresPermission` +
  `RequiresPermissionInterceptor` + its `WebMvcConfigurer`.
- **settings:** `SettingsService` (common), `SettingsController`/`SettingDto`/requests +
  `SettingsWebAutoConfiguration` (admin).
- **roles:** `RoleService` + `RoleServiceAutoConfiguration` (auth), `RoleController`/`RoleDto`/
  requests + `RoleAdminAutoConfiguration` (admin); `RoleConflictException`/`UnknownPermissionException`.
- **accounts:** `AccountService` + `AccountDto` + `AccountServiceAutoConfiguration` (auth),
  `AccountController`/`AccountUpdateRequest` + `AccountAdminAutoConfiguration` (admin);
  `UnknownRoleException`/`UnknownRealmException`; entity write-surface on `Account`/`Role`.
- **migrations:** `V4__seed_admin_role.sql`. (V2/V3 land the settings/RBAC tables in-milestone.)
- **harness:** `.githooks/pre-commit` main-guard + `pre-push`; `.claude/agents/reviewer.md`; CC
  PostToolUse formatter + Stop full-verify hooks (both repos, byte-identical); two `[ready]` skills.

## Tasks (atomic — as executed)
1. Stateless admin host + contour policy filter (`6ceb6dc`).
2. Local dev profile example (`2e1d351`).
3. Settings CRUD backend + framework contract; mount common via auto-config (`bebbc98`).
4. Dev postgres → host port 5433 (`f07842e`).
5. Access-mgmt foundation — repo registration, entity mutators, seed ADMIN role (`ff82e52`).
6. Roles CRUD + permission catalog (`3b373e3`).
7. Accounts CRUD-manage — status, realms, roles (`6132630`).
8. engine-screen-mounting playbook `[ready]` (`96a8832`); CC format/verify hooks (`05d2699`).

## Testing strategy (as-built)
- **Unit / slice (JUnit 5):** `AdminApplicationTest` (stateless boot; each API absent without its
  store), `AdminContourFilterTest` (401/200/403).
- **Integration (Testcontainers Postgres 18, Flyway, `ddl-auto=validate`):** `SettingsCrudIntegrationTest`,
  `RolesCrudIntegrationTest`, `AccountsCrudIntegrationTest`, `AccountServiceIntegrationTest`,
  `AdminRoleSeedIntegrationTest`.

## DoD (as-built, all met for the admin track)
- `admin` boots stateless (no Postgres) AND DB-backed by a one-line profile flip.
- Each admin API (settings/roles/accounts) mounts only when its store exists (proven both ways).
- Contour: anon→401, wrong-realm→403; per-handler `@RequiresPermission` → 403 as ProblemDetail.
- Errors are RFC 9457 `application/problem+json`; validation carries `fieldErrors`.
- `secret_hash` never appears in any response body (structural + test-pinned).
- `mvn -B verify` green across the reactor; every decision in `docs/sources-log.md`.

## Open questions
**N/A — reconstructed post-hoc.** In-pass forks are recorded as Decisions. Deferred forward:
account self-lockout guard (needs a subject→account resolver from the DB-login milestone).

## Related features / dependencies
- Depends on `01-common`, `02-auth`.
- **Paired plan:** `03-app-admin-shell.md` in `nobilis-platform-front` (Angular admin app).
- **Still open in `03`** (not admin-track): app-host (boot fix done on `app-host-boot`),
  extension points (BL-001, deferred), CMS screen, notifications-config screen, integrations screen,
  app-portal frontend.
- Feeds `04` (integration bus — producers live in app/admin) and `07` (domain on top).

## Risks (as-built)
- `@ConditionalOnBean(EntityManagerFactory)` timing — recurred 3× (settings/roles/accounts); the
  lesson is now in the mounting playbook.
- `@RestController` scan-exclude — missed once (accounts, the third site); now a checklist item.
