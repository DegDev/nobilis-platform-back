# Plan: 02-auth — identity, RBAC, thick token, config-admin login

> **RECONSTRUCTED POST-HOC (2026-07-09).** This milestone was built without running `/plan` first.
> This file is reconstructed from the git history (`02-auth` commits `85f7fa4`…`4ed4bc7`, merged in
> PR #2) and the `02-auth` rows of `docs/sources-log.md` — the actual source of truth. The **Recon**
> and **Open questions** sections are therefore not genuine pre-code sections; they are marked N/A or
> record the final resolved state, not what was unknown beforehand. Everything else (Goal, Decisions,
> Spec, Tasks, DoD) is factual — it describes what was actually shipped.

## Feature ID
`02-auth` (milestone; depends on `01-common`, done)

## Scope
Backend only. Engine `auth` module (library jar, contributes controllers via auto-config; the
runnable host `admin` arrives at `03`). No frontend existed at this milestone.

## Applicable playbook
- `docs/playbooks/` — **none fitted yet** at the time. The lombok-conventions playbook was extracted
  during this milestone (`8861daf`, first `[ready]`); the auto-config mounting pattern was captured
  later at `03`.

## Recon (before code)
**N/A — reconstructed post-hoc.** No pre-code recon file was written. The premises that a real recon
would have surfaced are recorded as resolved facts under "Decisions" and "Architectural decisions"
below (e.g. the sibling-package component-scan gap, the Boot-4 `.imports` auto-config path).

## Decisions (as-built, from sources-log)
1. **Opt-in via feature-flag auto-configuration**, not blanket component scan — a capability is off
   until the host enables it (engine/domain boundary rule).
2. **Config-admin is a stateless bootstrap path**, deliberately DB-free — not a missing migration.
3. **Permissions are string constants, not a Java enum** — a domain product ships its own catalog
   without recompiling the engine.
4. **Thick token** (realms + permissions inside the signed JWT) — authorize with no per-request DB
   round-trip; accepted trade-off is non-instant revocation (mitigate later with short TTL).

## Goal
Give the engine identity + role-based access control + a signed-token auth mechanism, plus a
DB-free config-admin login, so `03` can stand a secured admin host on top — all opt-in, no host
forced to enable auth.

## Architectural decisions (as-built — full provenance in `docs/sources-log.md`)

- **Opt-in mechanism = `@AutoConfiguration` via `META-INF/spring/…AutoConfiguration.imports`** (the
  Boot-4 path; `spring.factories` autoconfig was removed in Boot 3). Classpath-driven discovery, not
  component-scan — so a host opts in purely by depending on the `auth` jar, sidestepping the
  sibling-package scan gap (a bare `@SpringBootApplication` never reaches `engine.auth.*`). Admin
  login gated by `@ConditionalOnProperty(nobilis.auth.admin-login.enabled=true)`.

- **JWT hand-rolled HS256 on JCA** (`Mac` + Base64URL + Jackson), mirroring the engine's
  hand-rolled-primitive house style (`common.crypto.GcmCipher`) — no `jjwt`/Nimbus. Constant-time
  signature check (`MessageDigest.isEqual`); `exp` enforced against an injected `Clock`. Password
  hashing is NOT hand-rolled — BCrypt delegated to `spring-security-crypto` behind a thin
  `PasswordHasher` façade.

- **B0.1 — account/identity/realm model: value memberships vs. audited entity.** Realm membership is
  an `@ElementCollection<Realm>` on `Account` (a value the account holds, no identity/audit).
  `AccountIdentity` IS a full audited `BaseEntity` (linking a login method is an audited event).
  `Account` has no natural key → keeps JVM identity (no `@EqualsAndHashCode`); `AccountIdentity`
  equals by `(providerType, externalId)`. Root table `account`, NOT `user` (SQL reserved word).

- **B0.2 — role/permission model.** `account_role` is an entity↔entity `@ManyToMany @JoinTable`;
  `role_permission` is an `@ElementCollection<String>` (permissions are values). Permissions are
  `String` constants in `EnginePermissions`, NOT an enum. `PermissionResolver` lives in the `account`
  package (the walk runs `account → role`; avoids a package cycle).

- **B0.3 — thick token + hand-rolled gate.** Realms + effective permissions ride inside the signed
  JWT. The gate is a hand-rolled `OncePerRequestFilter`, NOT Spring Security — a pure MECHANISM that
  never rejects (missing/invalid token proceeds unauthenticated); deciding the admin contour is the
  host's job at `03`. Wire stays backward-compatible (thin 2-arg `issue` overload; null-tolerant
  claim reads).

- **B0.4 — config-admin stateless bootstrap.** `AdminLoginService` authenticates the single
  config-admin from properties and issues a thick token (`realms=[ADMIN]` + all `EnginePermissions`)
  with NO `account` row — by design, so owner access works against an empty/broken DB. DB-backed
  accounts arrive later with the identity providers and stand alongside it, never replace it.

## Spec — files created (backend, module `auth`)

- **Model (B0.1):** `Account`, `AccountIdentity`, `Realm` (enum), `ProviderType` (enum) — entities
  + `@ElementCollection` realm memberships; `account` table.
- **RBAC (B0.2):** `Role`, `EnginePermissions` (String catalog), `account_role` join,
  `role_permission` element collection, `PermissionResolver` (in `account` package).
- **Token (B0.3):** `JwtService` (HS256 on JCA, thick `issue`, null-tolerant `validate`),
  `JwtProperties`, `JwtAuthenticationFilter` (`OncePerRequestFilter` mechanism, never rejects),
  `PasswordHasher` (BCrypt façade).
- **Config-admin (B0.4):** `AdminLoginService`, `AdminLoginController`, `AdminLoginProperties`,
  the `@AutoConfiguration` + `.imports` registration, `@ConditionalOnProperty` gate.
- **Deps added:** `spring-webmvc` (MVC API, no embedded server), `jackson-databind`,
  `spring-security-crypto` — all Boot-BOM-managed.

## Tasks (atomic — as executed, one pass each)
1. Opt-in autoconfig mechanism + admin login (`e4add8b`).
2. B0.1 — account + identity + realm entities (`85f7fa4`).
3. B0.2 — role + permission + account-role RBAC (`4c3a67b`).
4. B0.3 — thick token + inbound permission gate (`a902315`).
5. B0.4 — lock config-admin as the stateless bootstrap path (`a471fad`).
6. Harness/hygiene alongside: lombok-conventions playbook (`8861daf`), sources-log backfill
   B0.1–B0.4 (`4ed4bc7`), JwtService tampered-signature de-flake (`ea5c8e6`).

## Testing strategy (as-built, JUnit 5)
- `AdminLoginAutoConfigurationTest` — `ApplicationContextRunner`: flag off → neither bean exists;
  config-admin issues a thick token with NO `AccountRepository` bean (locks the DB-free contract).
- `JwtServiceTest` — issue/validate round-trip, tampered signature rejected (constant-time),
  `exp` against a fixed `Clock`.
- RBAC + model persistence covered where entities are exercised.

## DoD (as-built, all met)
- `auth` builds as a library jar; a host mounts it by dependency only (no scan wiring).
- Admin login is off by default; on only with `nobilis.auth.admin-login.enabled=true`.
- Config-admin mints a thick token with no `account` row (test-locked).
- Thick token carries realms + permissions; old thin tokens still validate.
- Permissions are extensible strings, not an enum.
- Every non-trivial decision recorded in `docs/sources-log.md`.

## Open questions
**N/A — reconstructed post-hoc.** Open forks from this milestone were resolved in-pass and are
recorded as Decisions above. (One item deferred forward: instant revocation vs. short TTL for the
thick token — still open, tracked wherever token TTL is next touched.)

## Related features / dependencies
- Depends on `01-common` (BaseEntity, crypto, auditing seam).
- Feeds `03-app-admin-shell`: the admin host mounts `auth`, adds the contour policy the gate
  deferred, and builds settings/roles/accounts admin APIs on this RBAC.

## Risks (as-built)
- Token correctness (signature, `exp`) — covered by `JwtServiceTest`.
- Revocation latency from the thick token — accepted, mitigation deferred.
