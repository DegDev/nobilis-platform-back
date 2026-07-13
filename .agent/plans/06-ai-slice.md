# Plan: AI module — metadata-driven AI-profile mechanism (milestone 06)

## Feature ID
06-ai-slice

## Supersedes
This is recon #2, REPLACING the prior version of this file (thin env-config `LlmClient` + deferred
DB/UI). Deg decided the core of milestone 06 is the FULL metadata-driven AI-profile mechanism
validated in a private prior project (demo/Compo B2B): pick an LLM provider per "purpose," fill a
data-driven form (fields come from a catalog, not hardcoded), save a profile, and that purpose then
calls the LLM via the chosen profile at runtime. This is a deliberate, Deg-approved departure from
the "thin metadata engine, defer to later" default — not a violation of that principle, don't
re-litigate it.

## Clean-room note
The demo/Compo code and DDL are PATTERN REFERENCE ONLY (private prior project; nobilis is public
OSS and release is gated on the IP agreement). Every entity/service/DTO below is written fresh
against nobilis conventions — no demo code or DDL is copied. Package `dev.compo.b2b.*` naming does
not enter nobilis.

## Scope
Fullstack this pass. Backend: `nobilis-platform-back` (`common` migrations, new `ai` module,
`admin` controller, `integration` consume-point). Frontend: `nobilis-platform-front` (`admin`
project) — the AI-profile screen is IN SCOPE this milestone (previously deferred; now pulled in
because the mechanism is only provable end-to-end with a working screen).

## Applicable playbooks
- `docs/playbooks/engine-screen-mounting.md` **[ready]** — this feature spans four modules
  (`ai`/`admin`/`common`/`integration`); every mount seam below is checked against it.
- `docs/playbooks/async-consumer.md` **[ready]** — `NotificationDispatchEventHandler` is the worked
  example; names "moderation/LLM jobs later" as an anticipated consumer. This slice's proof-of-pipe
  is that case arriving, purpose-keyed (see Fork 3).
- `lombok-conventions.md` **[ready]** — applies to any new record/service/entity.
- Frontend: `angular22-verification` skill — AOT build + playwright verification for the new screen
  (milestone is well past 03, playwright is mandatory for this UI).

## Goal
A metadata-driven AI-profile mechanism: an admin screen where a "purpose" (a string key) is bound
to one provider (Ollama only this milestone) via a saved `AiProfile`, with the field set for that
provider rendered from a DB-seeded catalog (not hardcoded per provider), operational params
editable and persisted, secrets encrypted at rest and never round-tripped in plaintext, an on-demand
health-check probing real reachability, and a minimal purpose-keyed async consume-point proving a
future domain consumer (moderation, milestone 07) can resolve-and-call without inventing a domain
event shape now. Port is provider-agnostic (Yandex/Claude cheap later); only Ollama is built.
NO domain classifier. NO embeddings/vector/RAG. NO token-cost/usage metering or limits (BL-005).

## Architectural decisions (forks)

### Fork 1 — Spring AI vs hand-rolled RestClient — LOCKED (carried from recon #1)
Hand-roll `RestClient`-based adapters, mirroring `TelegramNotificationTransport.java:30,49,54,65-71`
(house style for third-party HTTP integrations — zero framework dependency, used twice already).
Rejected: `spring-ai-starter-model-ollama`, contradicts `ai`'s stated thin/opt-in scope
(`ai/pom.xml:17`).

### Fork 2 — 7-table schema → nobilis mapping — PROPOSED, confirm at GATE-0
Demo shape: 6 JPA entities + 1 DDL-only join table (`ai_provider_purpose`, no Java entity in demo,
native-query only via `AiProviderRepository.findForPurpose`).

| Table | Nature | nobilis placement |
|---|---|---|
| `ai_provider` | catalog, PK=`code` varchar | entity in `ai`, varchar PK already convention-correct |
| `ai_provider_purpose` | pure join (purpose, provider_code, sort_order) | composite-PK join table per `docs/conventions.md` join-table rule; **pick at GATE-0**: minimal `@Embeddable`-keyed entity vs. native-query-only (demo's own choice, reasonable precedent) |
| `ai_provider_field` | catalog, surrogate id | entity in `ai`; name already sorts adjacent to `ai_provider` — keep |
| `ai_field_option` | catalog, surrogate id, FK `field_id` | entity in `ai`; naming nit — `ai_field_option` sorts under `ai_field` not `ai_provider_field`; **pick at GATE-0**: keep demo naming vs. rename `ai_provider_field_option` |
| `ai_profile` | **BaseEntity** candidate, UNIQUE `purpose` | entity in `ai`, extends `BaseEntity` (audited, one profile per purpose enforced by DB constraint, not a separate "purpose" table — purpose stays a plain varchar key used consistently across `ai_provider_field.purpose`/`ai_provider_purpose.purpose`/`ai_profile.purpose`) |
| `ai_profile_param` | join/value table, FK `profile_id` | **pick at GATE-0**: composite PK (`profile_id`,`field_key`, convention-pure) vs. surrogate id (matches demo, simpler upsert) |
| `ai_secret` | keyed store, PK=`ref` varchar | entity in `ai`; **reuse `CryptoService`** (already exists, `common/.../crypto/CryptoService.java`, gated by `CryptoAutoConfiguration`) instead of a new cipher — demo hand-rolled `AesGcmCipher` because it predates a shared one, nobilis doesn't need to. Structurally mirrors `Setting` (`common/.../settings/Setting.java:47`, key/value/secret shape) |

**Module placement (settled, not a fork):** `common ← {ai, auth} ← {app, admin, integration}`
(`CLAUDE.md:22-26`). `ai` is a peer of `auth`. `auth` already carries its own JPA entities with its
own `AuthPersistenceAutoConfiguration` (`@AutoConfigurationPackage(basePackages =
"io.github.degdev.engine.auth")`) — `ai`'s 6 entities + services mirror this shape 1:1. Migrations,
however, per BL-003 (decided), live physically in `common/src/main/resources/db/migration`
regardless of which module's classes own the tables — same as `auth`'s migrations today.
`admin/pom.xml` currently depends on `common`+`auth` only — adding `ai` is required, architecturally
identical to the existing `auth` dependency. `integration/pom.xml` currently depends on `common`
only — adding `ai` is required (for the consume-point's `LlmClient` call).

**Decision needed:** the two "pick at GATE-0" naming/PK choices above.

### Fork 3 — async consume-point placement, purpose-keyed — REVISED from recon #1
Recon #1's option (a) — a `PingEventHandler`-style minimal `EventHandler` — still stands, revised
for the fuller scope: since "purpose" is now a real concept, dispatch is purpose-keyed rather than
provider-keyed. A single `EventHandler` bean reads `purpose` out of the event payload, calls
`AiProfileService.getActiveProfile(purpose)`, resolves the provider's `LlmClient`, calls it, logs
the response. Mirrors `NotificationDispatchEventHandler`'s transport-lookup-by-key pattern
(`NotificationDispatchEventHandler.java:60-73`) but keyed by a DB-driven string, not a fixed enum.
Still proof-of-pipe only — no new domain event shape, no classifier, same minimalism as
`PingEventHandler.java:30-45`.

**Decision needed:** confirm this purpose-keyed single-handler shape, and the `integration` package
name (`ai/` vs `dispatch/`, open since recon #1) at GATE-0.

### Fork 4 — opt-in gating shape — LOCKED (carried from recon #1)
`@ConditionalOnProperty` autoconfig mirroring `CryptoAutoConfiguration.java:32-35`, for the
`LlmClient`/`OllamaLlmClient` piece specifically (no DB dependency). The DB-backed pieces (entities,
`AiProfileService`, `AiProviderDefaults`, `AiSecretStore`) get a SEPARATE, unconditional
`AiPersistenceAutoConfiguration` (mirrors `AuthPersistenceAutoConfiguration`) plus a
`@ConditionalOnBean(EntityManagerFactory.class)`-gated service autoconfig (mirrors
`RoleServiceAutoConfiguration`/`AccountServiceAutoConfiguration`) — `AiSecretStore` additionally
gates on `CryptoService` being present (two-collaborator `@ConditionalOnBean`, mirrors
`SettingsAutoConfiguration.java:75`). See Mount seams below for the full table.

### Fork 5 — Ollama wire format: native `/api/chat` vs OpenAI-compat `/v1/chat/completions` — **UNRESOLVED, GATE-0 BLOCKER**
Recon #1 locked an OpenAI-compat shape (`LlmRequest{model, messages, temperature, topP, maxTokens}`,
`POST /v1/chat/completions`) from generic Ollama/context7 docs. This recon found the demo's actual,
working, battle-tested Ollama integration does NOT use that endpoint: it calls Ollama's **native**
`/api/chat` (`OllamaTranslationProvider.java:57`), with a nested `options` bag
(`{temperature, top_p, num_predict}`, lines 210-224) and parses `message.content` /
`prompt_eval_count` / `eval_count` / `done_reason` (lines 249,259-260,253) — none of which are
OpenAI-compat shapes. `num_predict` is used as-is, never translated to `max_tokens`. The
health-check probe also hits Ollama's native `/api/tags` (`AiHealthCheckService.java:73`), not
`/v1/models`.

**Trade-off, not resolved by this recon:**
- **Native `/api/chat`** — proven working shape (this exact code round-trips against real Ollama
  today in the source project), richer telemetry (`prompt_eval_count`/`eval_count`/`done_reason`
  vs. OpenAI-compat's minimal `usage` block), one Ollama-specific failure mode to special-case
  (200 OK with an `{"error":...}` body, `OllamaTranslationProvider.java:243-247`).
- **OpenAI-compat `/v1/chat/completions`** — recon #1's assumption, unproven in either codebase, but
  a cleaner "provider-agnostic" story for a future second (non-Ollama) provider — the demo's own
  `AiHealthCheckService.checkYandexAiStudio` (lines 103-131) confirms Yandex speaks OpenAI-compat,
  so a second adapter would reuse more code under this choice.

This determines the field-catalog key names (`max-tokens` vs `num-predict`), the response-parsing
shape, and whether a translation layer exists at all — it must be picked **before** the field
catalog (Fork 2's `ai_provider_field` seed) and the `LlmClient` interface are built, since slice 4
(client) blocks slices 3/5 (screen fields) on this choice.

**Decision needed: Deg picks native vs. OpenAI-compat before GATE-0/build starts.**

### Fork 6 — config source (infra vs operational split) — LOCKED (carried from recon #1, extended)
`base-url` stays an env-only `@ConfigurationProperties` value (crypto-style), since it must satisfy
Fork 4's boot-time gate and varies per-environment (dev vs prod Ollama host) — matches recon #1's
original reasoning. This is now scoped narrower than recon #1 framed it: **operational** params
(`model`, `temperature`, `top-p`, `num-predict`/`max-tokens`) are DB-driven via `ai_provider_field`/
`ai_profile_param` (this recon's addition, Fork 2) — that DB storage doesn't conflict with Fork 4's
boot gate because it's not what gates module opt-in, only `base-url`'s presence is. No Settings
(`integration.<provider>.*`) screen wiring this slice — the catalog IS the admin-editable surface
now, so recon #1's "defer to a later Settings-screen slice" concern is resolved differently: solved
by this milestone's own mechanism, not deferred.

## Catalog seeding (thinnest viable, Ollama-only)
Static Flyway seed (mirrors `V20260710120002__seed_admin_role.sql`'s idempotent `ON CONFLICT DO
NOTHING` pattern), landing in `common/.../db/migration` per BL-003:
- One `ai_provider` row: `code='ollama'`, `requires_local=true`, `sort_order=0`.
- One `ai_provider_purpose` row linking it to one seeded purpose (name TBD at GATE-0).
- `ai_provider_field` rows: `base-url` (infra, string), `model` (operational, string, editable),
  `temperature`/`top-p`/`num-predict-or-max-tokens` (operational, number, bounded). No `select`/
  `multiselect` needed for one provider — `ai_field_option` stays empty this milestone.
- No `ai_secret` seed (Ollama needs no key).
- `ai_profile`/`ai_profile_param` are NOT seeded — created by the admin screen's first save, proving
  the end-to-end write path.

## Health-check
Direct structural mirror of `AiHealthCheckService.checkOllama` (`AiHealthCheckService.java:68-81`):
a hand-rolled `RestClient` (not `RestTemplate` — demo predates nobilis's `RestClient` house style)
probe doing `GET {baseUrl}/api/tags`, parsing `models[].name`, checking the configured model is
present (with the same `:latest`-suffix fallback logic, lines 164-172 — worth mirroring in spirit).
Returns a simple `{ok, message}` DTO — human message only, never a raw exception/stack (matches
`TerminalBusException`/`RetriableBusException` message-not-stack discipline elsewhere). Invoked
on-demand via its own action, separate from save (not run automatically on every save).

## Mount seams (per engine-screen-mounting playbook)

| Piece | Module | Seam |
|---|---|---|
| 6 entities + repos | `ai` | new `AiPersistenceAutoConfiguration`, unconditional `@AutoConfigurationPackage`, mirrors `AuthPersistenceAutoConfiguration.java:54-56` |
| `AiProfileService`/`AiProviderDefaults`/`AiSecretStore` | `ai` | `@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)` gated `@ConditionalOnBean(EntityManagerFactory.class)`; `AiSecretStore` additionally gates on `CryptoService` (two-collaborator, mirrors `SettingsAutoConfiguration.java:75`) |
| `LlmClient`/`OllamaLlmClient` | `ai` | separate `@ConditionalOnProperty`-gated `@AutoConfiguration` (Fork 4), independent of the JPA gate — no DB dependency |
| `AiProfileController` | `admin` | new `AiAdminAutoConfiguration` gated `@ConditionalOnBean(AiProfileService.class)`, `after` its service autoconfig, mirrors `SettingsWebAutoConfiguration.java:38-39` — **must** be added to `AdminApplication`'s `@ComponentScan` exclude filter (the mandatory half-mount trap the playbook calls out) |
| `admin/pom.xml` | `admin` | add `ai` dependency (currently absent — only `common`+`auth`) |
| migrations | `common` | new timestamp-named `.sql` in `common/.../db/migration`, per BL-003 — NOT a new `ai/.../db/migration` folder |
| consume-point `EventHandler` | `integration` | new package (Fork 3), plain `@Component`, `EventHandler` interface, no extra gating beyond `PingEventHandler`'s bus-adapter condition; `integration/pom.xml` needs `ai` added (currently only `common`) |
| AI-profile screen | `nobilis-platform-front` `admin` project | new territory — no existing data-driven-form precedent in the front repo; feature-first folder (`ai/` or `settings/ai/`), Signal Forms, `httpResource` |

## Vertical-slice cut (thin-end-to-end-first)
1. **Schema + catalog** [LIGHT] — 6 entities + join table (Fork 2 picks), `AiPersistenceAutoConfiguration`,
   one Flyway seed (Ollama + one purpose). DoD: mount-present/absent test.
2. **Profile resolve/save service + secret store** [LIGHT-MEDIUM] — `AiProfileService`,
   `AiProviderDefaults`, `AiSecretStore` (reusing `CryptoService`), gated service autoconfig. No web
   layer yet. DoD: unit/integration tests — resolve-with-no-saved-profile (catalog default),
   save-then-resolve (override), secret round-trip.
3. **`LlmClient` + `OllamaLlmClient` + health-check probe** [HEAVY — Fork 5 must be resolved first] —
   real HTTP round-trip, param-mapping locked, health-check `RestClient` probe. DoD: mocked-HTTP unit
   tests + manual smoke test against native Ollama.
4. **Admin controller + descriptor/state/save/health-check endpoints** [MEDIUM] — `admin` module,
   mirrors `SettingsController`/`RoleController` shape. DoD: mount-present/absent +
   `*CrudIntegrationTest`.
5. **Frontend data-driven form** [MEDIUM-HEAVY, new territory] — purposes menu, provider select,
   descriptor-driven Signal Forms field rendering (Angular 22 equivalent of the demo's
   FormGroup-per-descriptor pattern), save + health-check actions. Spartan UI — visual polish is a
   later design milestone. DoD: Vitest for rendering-from-descriptor logic + playwright verification
   against the running admin app.
6. **Async consume-point** [LIGHT] — purpose-keyed `EventHandler` proof-of-pipe in `integration`
   (Fork 3). Depends on slice 3 (needs a real `LlmClient`).

Recommended order: 1 → 2 → 3 (can start in parallel with 4 once slice 2 exists, but 4's field
descriptors depend on Fork 5's wire-format pick landing first) → 4 → 5 → 6.

## Files to create / change (proposed, confirm at GATE-0)

### Backend
- `ai/src/main/java/io/github/degdev/engine/ai/` — entities (`AiProvider`, `AiProviderField`,
  `AiFieldOption`, `AiProfile`, `AiProfileParam`, `AiSecret`, + `ai_provider_purpose` per Fork 2's
  pick), repositories, `AiProfileService`, `AiProviderDefaults`, `AiSecretStore`, `LlmClient`,
  `OllamaLlmClient`, DTOs (`ResolvedAiProfile`, `AiFieldDescriptor`), `AiPersistenceAutoConfiguration`,
  service autoconfig, `LlmClient` autoconfig.
- `ai/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — new, registers all three `ai` autoconfigs.
- `admin/src/main/java/.../ai/` — `AiProfileController`, `AiAdminAutoConfiguration`, request/response
  DTOs (descriptor/state/save/health-check).
- `admin/pom.xml`, `integration/pom.xml` — add `ai` dependency.
- `common/src/main/resources/db/migration/<timestamp>__ai_profile_mechanism.sql` — 6-7 tables +
  Ollama-only catalog seed.
- `integration/.../ai/` (or `dispatch/`, pick at GATE-0) — purpose-keyed `EventHandler`.
- `AdminApplication`'s `@ComponentScan` exclude filter — add the new `ai` admin package.
- Runnable's local-properties example — `nobilis.ai.base-url` placeholder.

### Frontend
- `nobilis-platform-front/projects/admin/src/app/ai/` (or `settings/ai/`) — the AI-profile screen:
  component, service (`ai-profile.service.ts`-equivalent calling the new admin endpoints), model
  types, descriptor-driven form rendering via Signal Forms.

## Open questions (forks above, plus)
1. Fork 2's two naming/PK picks (`ai_provider_purpose` entity-or-native-query;
   `ai_profile_param` composite-vs-surrogate PK).
2. Fork 3's `integration` package name (`ai/` vs `dispatch/`).
3. **Fork 5 — the GATE-0 blocker: native `/api/chat` vs OpenAI-compat `/v1/chat/completions`.**
4. Exact seeded purpose name for the first slice (placeholder only; needs a real name before the
   Flyway seed is written).
5. Ollama automated-test approach — same three options recon #1 raised (Testcontainers vs. assume
   native `localhost:11434` vs. mock the HTTP boundary) — recommendation still (c) mock for the
   automated suite + manual smoke test in the DoD, per `stack.yml`'s native-Ollama rationale.

## Testing strategy

### Backend
- Unit tests: `AiProfileService` (resolve defaults, save/override, bounds validation),
  `AiSecretStore` (encrypt/decrypt round-trip via `CryptoService`), `OllamaLlmClient` (mocked
  `RestClient`, success + error-path, including the 200-OK-with-error-body case if Fork 5 picks
  native), health-check probe (mocked reachable/unreachable/model-missing cases).
- Integration tests: `AiProfileController` CRUD (`*CrudIntegrationTest` pattern), mount-present/
  absent test for `ai`'s autoconfigs.
- Unit test: the purpose-keyed `EventHandler`, mocked `AiProfileService`/`LlmClient`.
- Manual/local smoke test: a real round-trip + health-check against a native Ollama install,
  recorded in the DoD.

### Frontend
- Vitest: descriptor-driven form-building logic (field rendering by type, provider-switch
  reload), save/health-check request shaping.
- Playwright: navigate the admin app, exercise the AI-profile screen end-to-end (pick provider,
  fill fields, save, run health-check), inspect rendered DOM + network + console.

## Related features
- Dependencies: `EventBus`/`EventHandler` port + Kafka adapter (milestone 04, closed);
  `CryptoService`/`CryptoAutoConfiguration` (existing, reused by `AiSecretStore`); `auth` module
  (structural precedent for `ai`'s module shape).
- Playbook: `async-consumer.md` — this slice is its named "LLM jobs later" case landing, purpose-keyed.
- Future: milestone 07 domain classifier consumes the async consume-point; BL-005 (token-cost/usage
  metering + limits) builds on top of the profile mechanism once a real cost-control need is stated.

## Out of scope (explicit — do not design, do not build)
- Domain classifier (spam/legit moderation) — milestone 07.
- Embeddings / vector / RAG.
- Providers beyond Ollama (Yandex/Claude) — port is provider-agnostic, only Ollama built this milestone.
- Token-cost/usage metering AND usage-limits (demo's `LlmCost`/`DailyUsageRow`/`ModelPriceSegment`/
  `LlmTokenUsage*`/`AiRateLimitApplier`/`LlmDailyUsageAggregator`) — logged as BL-005.
- Visual polish of the admin AI screen — spartan, matches current screens; design milestone is after 06.
- The admin-navigation sidebar-as-data UX idea — already logged as BL-004.

## DoD
- [ ] Fork 5 (wire format) resolved by Deg before any `LlmClient`/catalog code is written.
- [ ] 6-7 entities + `AiPersistenceAutoConfiguration` exist in `ai`, mount-present/absent verified.
- [ ] `AiProfileService`/`AiProviderDefaults`/`AiSecretStore` exist, gated service autoconfig,
      `AiSecretStore` reuses `CryptoService`.
- [ ] `LlmClient`/`OllamaLlmClient` round-trip a real prompt against native Ollama (manual smoke
      test) + pass mocked-HTTP unit tests.
- [ ] Health-check probe returns correct `{ok, message}` for reachable/unreachable/model-missing.
- [ ] `AiProfileController` exposes purposes/providers/descriptor/state/save/health-check, mounted
      only when `AiProfileService` is present, excluded correctly from `AdminApplication` component
      scan.
- [ ] Admin frontend screen renders fields from the descriptor (not hardcoded), saves, runs
      health-check — verified via playwright in the running app.
- [ ] Purpose-keyed `EventHandler` proof-of-pipe exists in `integration`, invents no domain event
      shape.
- [ ] `mvn -B verify` green across the full reactor; frontend `ng build admin --configuration=development`
      + Vitest green.
- [ ] `docs/sources-log.md` rows added for each locked fork's rationale.
- [ ] `docs/architecture-backlog.md` BL-005 entry added (token-cost/usage metering + limits).
