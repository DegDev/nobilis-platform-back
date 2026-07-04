# Engine screen mounting — nobilis-platform-back

**Type:** reusable playbook (wiring recipe) &middot; **Scope:** backend only (a JVM / Spring Boot
auto-configuration pattern — no frontend counterpart) &middot; **Status:** ready (extracted from
three real instances) &middot; **Updated:** 2026-07-04

**Reference (real files this playbook is extracted from):**

- **Settings** (pass 3a) — service+package: `common/.../settings/SettingsAutoConfiguration.java`;
  web: `admin/.../settings/SettingsWebAutoConfiguration.java`,
  `admin/.../settings/SettingsController.java`, `admin/.../settings/SettingDto.java`.
- **Roles** (pass 4b) — service: `auth/.../role/RoleServiceAutoConfiguration.java`; web:
  `admin/.../roles/RoleAdminAutoConfiguration.java`, `admin/.../roles/RoleController.java`,
  `admin/.../roles/RoleDto.java`; force-init: `auth/.../role/RoleService.java`.
- **Accounts** (pass 4d) — service: `auth/.../account/AccountServiceAutoConfiguration.java`; web:
  `admin/.../accounts/AccountAdminAutoConfiguration.java`,
  `admin/.../accounts/AccountController.java`; read-model + lazy init:
  `auth/.../account/AccountService.java`, `auth/.../account/AccountDto.java`.
- **Shared** — persistence package registration:
  `auth/.../persistence/AuthPersistenceAutoConfiguration.java`; the host scan-exclude:
  `admin/.../AdminApplication.java`; mount present/absent proof:
  `admin/.../AdminApplicationTest.java` + each screen's `*CrudIntegrationTest`.

Provenance and rationale: `docs/sources-log.md` — the **03-app-admin-shell** rows for passes 3a
(settings), 4b (roles) and 4d (accounts). This file is the WIRING recipe extracted from them; it
does not restate the per-feature decisions.

## WHEN to apply

Bringing a new engine **admin screen's backend** online: an entity + repository that already exist,
a service that manages them, and a `@RestController` that exposes them under `/admin/api/**`. The
goal is that the screen mounts **only when its store (a database) exists**, and **never
half-mounts** on the stateless default host. Follow this the moment you add a controller that needs
a JPA-backed service — it is nobilis's **locked** mounting convention, proven three times; do not
invent a different wiring.

## What it is (and why it is not obvious)

The admin host boots **stateless by default** — `common` drags JPA/Hibernate/Flyway onto the
classpath, but the default profile configures no datasource, so there is no `EntityManagerFactory`
(EMF). A DB screen must therefore appear **conditionally**: present when a profile turns the
database on, absent otherwise. Getting that conditional right has three parts and two adjacent
traps — each of which bit a real screen before it was understood.

## The three-part shape

### 1. Service — an `@AutoConfiguration` gated on the **EMF**, never on the repository

The service that owns the screen's logic is contributed by a `@Bean` in an `@AutoConfiguration`
gated `@ConditionalOnBean(EntityManagerFactory.class)` and ordered
`@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)`. The repository is **injected into
the `@Bean` method** (resolved at instantiation, when it certainly exists), not used as the gate.

- Roles — `RoleServiceAutoConfiguration.java:39-40` (`@AutoConfiguration(after =
  HibernateJpaAutoConfiguration.class)` + `@ConditionalOnBean(EntityManagerFactory.class)`).
- Accounts — `AccountServiceAutoConfiguration.java:40-41` (byte-for-byte the same shape).
- Settings — the service `@Bean` is **method-gated** `@ConditionalOnBean({EntityManagerFactory,
  CryptoService})` (`SettingsAutoConfiguration.java:75`), i.e. the same EMF condition plus the extra
  collaborator it needs.

**WHY not `@ConditionalOnBean(TheRepository)`:** `@ConditionalOnBean` only sees bean definitions
**already contributed** when the condition runs, and the JPA-repositories auto-configuration's
ordering relative to your class is not guaranteed. Gate on the repository and the service may
**silently never mount** (worked in the persistence module's own test, 404 in the host). The EMF
bean, by contrast, is reliably present once `HibernateJpaAutoConfiguration` has run — so `after` it
and gate on the EMF.

### 2. Persistence package — `@AutoConfigurationPackage` stays **UNCONDITIONAL**

The entity + repository are discovered by adding their module's base package to Boot's
auto-configuration packages (which both the JPA entity scan and the Spring Data repository scan
consume). This registration is **never** EMF-gated.

- Auth (split shape) — `AuthPersistenceAutoConfiguration.java:55`
  (`@AutoConfigurationPackage(basePackages = "io.github.degdev.engine.auth")`), unconditional.
- Common (combined shape) — `SettingsAutoConfiguration.java:63` carries the same unconditional
  `@AutoConfigurationPackage` on the class, while only the *service `@Bean`* (part 1) is gated.

**WHY unconditional:** `@AutoConfigurationPackage` runs during **configuration parsing**, before any
EMF bean exists. Gate it on the EMF and the package is registered **after** Spring Data's repository
scan has already run → the repositories never become beans, even on a real JPA host. It does not
half-mount on the stateless host anyway: with no EMF nothing scans the package, so no repo beans
appear (locked by `AdminApplicationTest#doesNotMountAuthRepositoriesWithoutADatabase`).

### 3. Web layer — host `@AutoConfiguration` gated on the service **AND** controller in the scan-exclude

The `@RestController` is registered as a `@Bean` by a host-side `@AutoConfiguration` gated
`@ConditionalOnBean(TheService.class)` and ordered `after` the service's auto-config — **and** the
controller **must** be added to the host's `@ComponentScan` exclude filter.

- Settings — `SettingsWebAutoConfiguration.java:38-39`; Roles — `RoleAdminAutoConfiguration.java:36-37`;
  Accounts — `AccountAdminAutoConfiguration.java:37-38` (all `@AutoConfiguration(after =
  …ServiceAutoConfiguration)` + `@ConditionalOnBean(…Service.class)`).
- The exclude — `AdminApplication.java:59-60`:
  `@Filter(type = ASSIGNABLE_TYPE, classes = {SettingsController, RoleController, AccountController})`.

**WHY the exclude is mandatory, not optional (the trap that bit all three):** `@RestController` is a
`@Component`, so a plain component-scan mounts it **even when** the conditional `@Bean` correctly
declines. The gate on the service is necessary but **not sufficient**: the accounts screen booted
its condition report saying *"did not find any beans"* for the service, yet the host still failed to
start because component-scan instantiated the controller with no service to inject. Excluding the
controller from the scan leaves the conditional `@Bean` as its **single** registration — mount it
via the service, or not at all.

## Two adjacent traps the same screens hit

- **Lazy collections must be materialized in the service transaction.** The web tests run
  `open-in-view=false`, so any `@ElementCollection` / `@ManyToMany` the DTO reads must be
  force-initialized **inside** the `@Transactional` read, or the detached mapping throws
  `LazyInitializationException`. Roles force one collection (`RoleService.java:160`,
  `role.getPermissions().size()`, applied in `list`/`find`); accounts materialize **two** plus a
  separate identity fetch by building the DTO in-tx (`AccountService.java:126,129,131`). `Page.map`
  in a `@Transactional` `list` runs eagerly, still inside the transaction — safe.
- **The DTO lives on the side that owns the read; a service never returns a host type.** The module
  graph is `common ← auth ← admin`, so a service in `auth`/`common` may not depend on an `admin`
  type. If the controller maps the entity, the DTO may live in the host (`RoleDto` in
  `admin/.../roles/`). If the **service** returns the DTO — because it must build it in-tx (lazy +
  extra fetch, as accounts does) — the DTO lives in the **service's** module (`AccountDto` in
  `auth/.../account/`, `AccountDto.java`). Never return the entity to be mapped after the
  transaction closes.
- **Secret-hash-like fields are structurally excluded from the DTO.** Do not rely on "don't
  serialize it" — leave the field off the record entirely. `AccountDto.IdentityRef`
  (`AccountDto.java:66`) is `(provider, externalId)` only, no `secretHash`; `SettingDto`
  (`SettingDto.java:34`) emits `value = null` for a secret rather than a placeholder. A field that
  is not there cannot leak.

## The mounting checklist (for the NEXT engine screen)

1. **Service** contributed by an `@AutoConfiguration` gated `@ConditionalOnBean(EntityManagerFactory)`
   + `after HibernateJpaAutoConfiguration` — **not** `@ConditionalOnBean(TheRepository)`.
2. **`@AutoConfigurationPackage`** for the persistence module stays **unconditional** (never
   EMF-gated).
3. **Controller** registered via a host `@AutoConfiguration` gated `@ConditionalOnBean(TheService)`,
   `after` the service auto-config.
4. **Controller added to the host's `@ComponentScan` exclude** (`AdminApplication`) — or it
   half-mounts via component-scan and the stateless host fails to boot.
5. **Lazy `@ElementCollection` / `@ManyToMany`** force-initialized **in the service transaction**
   (`open-in-view=false`) before DTO mapping — or `LazyInitializationException`.
6. **DTO lives in the module that owns the read** (the entity's side); a service never returns a
   host (`admin`) type.
7. **Any `secret_hash`-like field is structurally absent** from the DTO record.
8. **Mount present AND absent both tested:** DB profile → the controller responds
   (`*CrudIntegrationTest`, e.g. `AccountsCrudIntegrationTest`); stateless host → the service and
   controller beans are **absent** (`AdminApplicationTest#doesNotMountThe…ApiWithoutAStore`,
   `AdminApplicationTest.java:99,108`).

## When NOT to force

- **A screen that does not need a store** (pure config/mechanism, no JPA) is NOT this pattern — it is
  the feature-flag opt-in shape (`@ConditionalOnProperty`), e.g. admin login. Do not wrap a
  storeless capability in an EMF gate.
- **Component-scanned `@Service`/`@Controller`** as a shortcut — rejected here on purpose: a
  component-scanned `@ConditionalOnBean` evaluates before auto-configs run and never matches, and a
  scanned `@RestController` is exactly the half-mount trap (part 3). The conditional `@Bean` in an
  `@AutoConfiguration` is the single registration.
- **Build/profile wiring** — how the stateless default expresses its `spring.autoconfigure.exclude`
  and how a profile resets it lives in `CLAUDE.md` / `docs/sources-log.md`, **not** here. This
  playbook is the mounting recipe, not datasource configuration.

## Links

- **Rationale / provenance** — `docs/sources-log.md`, the **03-app-admin-shell** rows (3a settings,
  4b roles, 4d accounts): why gate on the EMF, why the package stays unconditional, why the
  scan-exclude is mandatory, the lazy-collection catch, and the read-model placement.
- **Where a class lives** (package-by-feature) — `CLAUDE.md` &rarr; *Package structure*. This
  playbook is about the WIRING that mounts a screen, not which package a class goes in.
- **Lombok style** for the service/DTO/entity classes involved — `docs/playbooks/lombok-conventions.md`.
