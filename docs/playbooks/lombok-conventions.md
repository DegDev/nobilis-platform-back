# Lombok conventions — nobilis-platform-back

**Type:** reusable playbook (code recipe) &middot; **Scope:** backend only (Lombok is JVM-only — no
frontend counterpart) &middot; **Status:** ready (extracted from the first real instances) &middot;
**Updated:** 2026-07-01

**Reference (real files this playbook is extracted from):**

- Entities / mapped superclass — `common/.../persistence/BaseEntity.java`,
  `common/.../settings/Setting.java`
- Services / controllers — `common/.../settings/SettingsService.java`,
  `auth/.../adminlogin/AdminLoginService.java`, `auth/.../adminlogin/web/AdminLoginController.java`
- Hand-written constructor (validation/decoding) — `common/.../crypto/CryptoService.java`,
  `auth/.../token/JwtService.java`, `auth/.../password/PasswordHasher.java`
- Records (immutable value carriers) — `auth/.../token/AuthClaims.java`,
  `auth/.../adminlogin/web/LoginRequest.java`, `auth/.../adminlogin/web/LoginResponse.java`,
  `auth/.../adminlogin/AdminLoginProperties.java`, `auth/.../token/JwtProperties.java`,
  `common/.../crypto/CryptoProperties.java`

Provenance and rationale: `docs/sources-log.md` (the **01-common** Lombok rows). This file is the
STYLE recipe extracted from them; it does not restate build wiring.

## WHEN to apply

Before creating **or editing** any data class, DTO, entity, or service in the backend. Pick the
class type below and follow the annotation set for it — this is nobilis's **locked** convention, not
a suggestion. Do not weaken it and do not introduce a stricter/different rule than what the cited
files already ship.

## Structure by class type

### Immutable value carrier → Java `record`, never Lombok `@Value`

A value that is built once and read — a DTO, a claims/result holder, a `@ConfigurationProperties`
binding — is a `record`, not a `@Value`/`@Builder` class and not a hand-rolled immutable class.
Locked decision: there is **zero** `@Value` in the codebase.

- Result / claims holder — `AuthClaims` (`auth/.../token/AuthClaims.java`): the verified JWT payload,
  `record AuthClaims(String subject, List<String> roles, Instant issuedAt, Instant expiresAt)`.
- HTTP request/response DTO — `LoginRequest` / `LoginResponse`
  (`auth/.../adminlogin/web/`): `record LoginRequest(String email, String password)` and the
  single-field `LoginResponse`.
- `@ConfigurationProperties` binding — `AdminLoginProperties`, `JwtProperties`, `CryptoProperties`:
  records annotated `@ConfigurationProperties(prefix = "...")`, `@DefaultValue` on defaults.

### Mutable DTO → `@Getter @Setter` + constructor annotation as needed, never `@Data`

A DTO that genuinely mutates after construction is `@Getter @Setter` with
`@NoArgsConstructor`/`@AllArgsConstructor` as the case needs — **never `@Data`** (see *When NOT to
force*). Note: the codebase currently has **no** mutable DTO — every DTO shipped so far is a `record`
(above). Prefer a record first; reach for a mutable `@Getter @Setter` bean only when something
actually has to change in place, and even then never `@Data`.

### `@Entity` (JPA) → `@Getter`, selective `@Setter`, protected no-args ctor, business-key equals

Two real shapes, both in `common`:

- **Read-only mapped superclass** — `BaseEntity` (`common/.../persistence/BaseEntity.java`):
  `@Getter` **only**, no `@Setter` anywhere. The id is database-generated, the `@Version` is
  provider-managed, the `@CreatedDate`/`@LastModifiedDate` timestamps are filled by JPA auditing —
  nothing is set by hand, so nothing gets a setter.
- **Concrete entity** — `Setting` (`common/.../settings/Setting.java`):
  - `@Getter` on the type;
  - `@Setter` **only on the genuinely mutable fields** (`value`, `secret`) — the natural/business key
    (`key`) stays setter-less and immutable after construction;
  - `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — satisfies Hibernate without widening the
    public API;
  - `@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)` with
    `@EqualsAndHashCode.Include` on the **business key only** (`key`) — never the generated id and
    never an association, so equality is stable across the lifecycle and can't trigger lazy loading;
  - a hand-written constructor for the real fields (Lombok generates only the protected no-args one).

**Never `@Data` on an entity** — it bundles `@EqualsAndHashCode`/`@ToString` over *all* fields, which
drags lazy associations into equality/`toString` (`LazyInitializationException`, N+1, broken `Set`
semantics).

### Services → `@RequiredArgsConstructor` + `@Slf4j`; hand-written ctor when the ctor has real logic

When a service/controller just needs its `final` collaborators injected, let Lombok write the
constructor:

- `SettingsService` (`common/.../settings/SettingsService.java`): `@Slf4j @Service
  @RequiredArgsConstructor` — Lombok injects the `final` `SettingRepository`/`CryptoService`, and
  `@Slf4j` provides the `log` field.
- `AdminLoginService` (`auth/.../adminlogin/`) and `AdminLoginController`
  (`auth/.../adminlogin/web/`): `@RequiredArgsConstructor` over their `final` collaborators (no
  logging needed, so no `@Slf4j`).

But when the constructor does **real work** — validating, decoding, deriving state — write it by
hand and **do not** add `@RequiredArgsConstructor`:

- `CryptoService` (`common/.../crypto/CryptoService.java`): `@Service`, hand-written constructor that
  Base64-decodes the master key and fails fast on a missing / non-Base64 / wrong-length key. Lombok
  can't express that logic, so the constructor is manual — this is correct, not a deviation.
- `JwtService` (`auth/.../token/JwtService.java`): the same pattern on the auth side — plain `final
  class`, hand-written constructor that decodes and length-checks the HMAC secret (fail-fast).
- `PasswordHasher` (`auth/.../password/PasswordHasher.java`): a plain `final class` with no injected
  collaborators (it news up its own `BCryptPasswordEncoder`), so there is nothing for a constructor
  annotation to wire — leaving it Lombok-free is correct.

### Manual boilerplate where Lombok would do it = deviation

Hand-writing getters/setters or a pure injection constructor that `@Getter`/`@Setter`/
`@RequiredArgsConstructor` would generate is a deviation — don't drift back to it. (A hand-written
constructor that carries **logic**, as in `CryptoService`/`JwtService`, is the opposite: correct, and
must stay manual.)

## Checklist

1. **New data class → pick by type above:** immutable value carrier → `record`; mutable DTO →
   `@Getter @Setter` (never `@Data`); JPA `@Entity` → entity recipe; service → service recipe.
2. **`@Entity`:** `@Getter` + `@Setter` on mutable fields only,
   `@NoArgsConstructor(access = PROTECTED)`,
   `@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)` with
   `@EqualsAndHashCode.Include` on the business key. Never `@Data`.
3. **Immutable value carrier → `record`** — not `@Value`, not `@Builder`, not a hand-rolled
   immutable class.
4. **Service/controller:** `@RequiredArgsConstructor` (+ `@Slf4j` if it logs) for pure injection;
   a **hand-written** constructor with no constructor annotation when the constructor validates /
   decodes / derives (`CryptoService`, `JwtService`).
5. **Manual getters/setters/injection-ctor that Lombok would generate → rewrite with Lombok.** Any
   Lombok conversion is surgical: annotations only, don't touch logic, tests green before and after.

## When NOT to force

- **`@Data` — never, anywhere.** On an entity it pulls lazy associations into equality/`toString`; as
  a blanket rule it hides which fields are really mutable. There is none in the codebase — keep it
  that way.
- **A class with custom `equals`/`hashCode`/`toString` or non-trivial accessor logic, or a
  constructor that validates/decodes** (`CryptoService`, `JwtService`, `PasswordHasher`) → partial
  Lombok (`@Getter` only) or hand-written. Don't break behaviour to save an annotation.
- **Build/toolchain wiring** — Lombok version/scope, root `lombok.config`, and the JDK 25
  `--add-opens` javac flags — lives in `CLAUDE.md` / `docs/sources-log.md`, **not** here. This
  playbook is STYLE only, not build config.

## Links

- **Where a class lives** (package-by-feature placement) is a separate concern — see `CLAUDE.md`
  &rarr; *Package structure — package-by-feature*. This playbook is about STYLE (which annotations),
  not WHERE the class goes.
- **Rationale / provenance** — `docs/sources-log.md`, the **01-common** Lombok rows (why `record`
  over `@Value`, why business-key-only `@EqualsAndHashCode`, why `CryptoService` keeps its manual
  constructor).
