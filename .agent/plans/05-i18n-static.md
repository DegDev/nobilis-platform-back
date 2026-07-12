# Plan: i18n static (milestone 05)

## Feature ID
05-i18n-static

## Scope
Fullstack (backend + frontend). Static UI-string i18n across both floors — backend `messages`
bundles via Spring `MessageSource`, frontend UI strings via `@angular/localize` runtime
`loadTranslations`. Locales: EN (native) + RU + RO overlays. Content i18n
(`entity_translation` + LLM auto-translate) is explicitly OUT of scope, deferred to a
post-first-slice milestone.

## Status
Pass 1 of 4 committed (`379830f`, backend). Passes 2-4 (frontend) pending. This plan was authored
post-hoc: milestone 05 ran recon + build through OpenAI Codex (multi-agent A/B), and Codex has no
`/plan` equivalent yet, so the canonical repo plan file was written after pass 1 landed. Working
copy of the forks lives in the Notion build-plan page; this file is the repo SSOT.

## Applicable playbook
- No `[ready]` playbook matches i18n directly. If a durable pattern emerges (backend MessageSource
  bridge, frontend runtime-localize bootstrap), extract it after the milestone lands — do not
  pre-write (extract-don't-predict).
- fullstack: paired front/back work, same feature-id in both repos.

## Architectural decisions

### Native language = EN (locked 2026-07-12, OVERRIDES milestone-01 "default RU")
nobilis is a public international engine; English source strings are the natural default and need no
justification, whereas a Russian native default in an international engine would be odd. RU/RO are
overlay translations layered on top in downstream projects.

Consequences:
- Existing `*.strings.ts` values ARE English and STAY English — milestone 05 assigns them `$localize`
  IDs, it does NOT translate them (Codex recon caught they are English, not Russian).
- `DEFAULT_LOCALE` flips `ru` → `en` in `LocaleResolver.java:36` (milestone-01 code edit, not drift).
- Supported locales become `en` / `ru` / `ro`; `messages_en.properties` is the base bundle.
- RU + RO = overlay dictionaries, EMPTY or partial this milestone — the point is to PROVE the
  mechanism switches and falls back to EN cleanly; population happens later in projects.
- Record BOTH decisions in sources-log: was-RU-at-01 (Moldovan product context), now-EN-at-05
  (public international engine).

### Frontend mechanism = @angular/localize runtime loadTranslations (locked 2026-07-12)
NOT ngx-translate / Transloco / build-per-locale. The ngx-translate mention in the spec is
superseded — Deg's working demo project uses `@angular/localize` with runtime `loadTranslations`,
and one pattern across projects beats per-project divergence. Pattern source (clean-room, PATTERN
only, COPY NO CODE): `demo-front/projects/compob2b-admin/src/app/admin-shared/i18n/i18n-init.ts`.

Mechanics to mirror:
- Native (EN) = built-in default with NO overlay dictionary.
- Each non-native locale = dynamic `import()` of a TS dict chunk + `fetch(assets/i18n/<locale>.json)`
  → `loadTranslations`.
- `registerLocaleData` for EVERY locale (else `DatePipe` NG0701).
- Don't crash without a dict file (`.catch` → stays native).
- Async init runs BEFORE the app import. In standalone (not NgModule), `provideAppInitializer` is TOO
  LATE — appConfig/App import before the injector exists. main.ts awaits `initI18n()`, THEN
  dynamically imports App + appConfig, THEN `bootstrapApplication()`.
- Switch = `location.reload()` — `loadTranslations` does not make already-evaluated strings reactive
  (intentional, mirrors demo).

Adapt from demo: standalone not NgModule; PrimeNG not Taiga (drop TUI_LANGUAGES); no column-filter
trap (do NOT copy the demo's param-stripping wrapper).

### Backend locale bridge = MVC LocaleResolver adapter (locked 2026-07-12)
A custom Spring MVC `LocaleResolver` bean delegating to the milestone-01 engine `LocaleResolver`.
NOT a naive filter calling `LocaleContextHolder.setLocale()` — Codex recon caught that
`DispatcherServlet` establishes its own request locale at dispatch and would overwrite it. The MVC
resolver is invoked by Spring normally and establishes `LocaleContextHolder` for controller
execution; `MessageSource` then resolves in the request locale. Reuses milestone-01's resolver
(validate + fallback).

### Locale transport = ?locale= query param (already decided, milestone 01)
The ONE transport for both static UI now and future content i18n — the platform is universal, both
mechanisms ride the same `?locale=`. `DEFAULT_LOCALE` constant in common is the single source; the
front mirrors it. (demo-back migrated everything from `/{locale}/` path prefixes to `?locale=` and
deleted the old mirrors — same contract choice.)

### HTTP charset = UTF-8, forced (locked 2026-07-12, "ONLY UTF-8")
HTTP responses force UTF-8 (`server.servlet.encoding.force=true` + a UTF-8
`StringHttpMessageConverter` in both runnables) so localized strings survive a non-UTF-8 server
locale. Caught in pass 1: a String body defaulted to ISO-8859-1 and mangled Cyrillic under a JVM
whose `sun.jnu.encoding` was not UTF-8 — a production defect for an i18n engine, not just a test
issue. Enforce as a barrier, not prose.

## Build passes

### Pass 1 — BACKEND — ✅ COMMITTED (379830f)
- Flip `DEFAULT_LOCALE` en; supported en/ru/ro (`LocaleResolver`, test updated).
- MVC `LocaleResolver` bridge (`QueryParamLocaleResolver` + `I18nWebConfiguration` in admin);
  delegates to engine resolver, `setLocale` read-only.
- `messages_en.properties` base + partial ru + empty ro overlays.
- Convert API-facing errors to message keys (`GlobalExceptionHandler`, exception classes across
  admin/auth/common).
- Force UTF-8 servlet + plain-text response encoding in both hosts.
- Tests: MessageSource resolves en vs present ru key, absent → en fallback, ?locale=ro MockMvc path,
  Cyrillic serialization under a non-UTF-8 JVM locale.
- `mvn -B verify` green on JDK 25 with `LC_ALL=C` / `sun.jnu.encoding=ANSI` (verified independently).

### Pass 2 — FRONTEND common — pending
Shared `locale/` capability in `projects/common/src/lib/locale/`: `Locale = 'en'|'ru'|'ro'`,
`DEFAULT_LOCALE = 'en'` mirror, supported list, validation, persisted active locale, signal-backed
locale service, the shared `?locale=` HTTP interceptor (append/replace, preserve existing params).
Export via `public-api.ts`. Unit tests: signal init/switch; interceptor with
`HttpTestingController` incl. requests already carrying query params.

### Pass 3 — FRONTEND admin + app — pending
Add `@angular/localize` (runtime, `--use-at-runtime` → dependencies). The async i18n-init mirrored
from demo, adapted to standalone: main.ts awaits init then dynamic-imports app + appConfig then
`bootstrapApplication()`. `registerLocaleData` en/ru/ro. Language switcher (persist +
`location.reload()`). Register the interceptor in both `app.config.ts`.

### Pass 4 — FRONTEND strings — pending
Assign `$localize` IDs to the ~182 existing English strings (`i18n="@@Id"` in templates,
`$localize`:@@Id:English`` in TS). `ng extract-i18n` workflow. `assets/i18n/ru.json` + `ro.json`
(empty or a few proof keys). Sanity spec (cyrillic-in-EN guard / placeholder integrity). `ng build`
AOT green (the ONLY full template check — `tsc`/`ng serve` miss orphan template errors) + Vitest.

Migration surface (Codex recon): 9 `*.strings.ts`, ~182 keys — landing(3), login(8), dashboard(9),
integrations(15), settings(24), roles(25), accounts(25), content-blocks(27), notifications(46).
Count grows if extraction catches direct template text / attributes / PrimeNG labels.

## DoD
- Backend: MessageSource resolves per `?locale=`, en fallback, UTF-8 responses. (pass 1 ✅)
- Frontend: admin + app switch EN↔RU↔RO; missing overlay → clean EN fallback; formats correct per
  locale; `ng build` AOT green.
- i18n applied in the same change as any user-visible string.
- No hardcoded secrets; correct branch (`05-i18n-static`); sources-log updated.
- Each pass = its own commit Deg reviews; STOP + report + proposed commit message per pass.

## Out-of-scope
- RO/RU string population (mechanism only; overlays empty or partial).
- Content i18n (`entity_translation`, LLM auto-translate) — later milestone.
- Reconsidering `?locale=` (locked) or the mechanism (localize-runtime, locked).

## Milestone-01 impact note
Milestone 01's plan fixed "i18n: default RU, fallback requested→RU" and milestone-01 code
implemented it (`LocaleResolver.DEFAULT_LOCALE=ru`, valid ru/ro). Milestone 05 REDEFINES this:
default EN, supported en/ru/ro. This is a deliberate edit of existing milestone-01 code, not drift —
both decisions recorded in sources-log.
