# CLAUDE.md — nobilis-platform-back

Project instructions for Claude Code. Read before every task. Keep it short and precise.

## What this is

**nobilis-platform** — a universal open-source web engine (back). A framework that domain
business logic is mounted on top of. This repository is the **public backend core**, a portfolio
piece. Its first consumer is the private home-service project (NOT part of the engine).

Name: `nobilis` (from Ubuntu 24.04 "Noble Numbat", released the day the project started).

## Stack (pin versions, never `:latest`)

- Java 25 (LTS) + Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11, Servlet 6.1, Tomcat)
- Maven, multi-module build
- PostgreSQL 18 (nailed down — no DB abstraction)
- Kafka 4.3.1 (KRaft, no ZooKeeper) — the bus
- MinIO (S3), Mailpit (dev SMTP), Ollama (local LLM)
- Dev infra: `docker compose -f stack.yml up -d`

## Modules and dependencies (STRICTLY one-directional)

```
common  ←  { ai, auth }  ←  { app, admin, integration }
```

- **common** (lib, jar) — entities, repositories, integration settings + crypto, i18n resolution. Pulled by all.
- **ai** (lib, jar, opt-in) — provider-agnostic LLM adapters. The engine builds without it.
- **auth** (lib, jar) — tokens, identity providers (email/password, Telegram, SMS) — opt-in controllers.
- **app** (runnable, HTTP) — portal shell + extension points for the domain.
- **admin** (runnable, HTTP) — CRUD framework + engine screens (access, CMS, notifications, settings).
- **integration** (lib + worker process, NO HTTP) — Kafka, notification dispatcher, transports, scheduler.

Runnables: app, admin, integration-worker. Libraries: common, auth, ai.
No cycles. Runnables do not depend on each other.

## Boundary: engine vs domain

This repo is ONLY the engine (capabilities). Domain specifics (order state machine, claim,
pay-per-lead, categories, Telegram bot logic, bank adapter, rating) live in the private
homeservice-back, NOT here. If a task sounds like "domain", it does not belong in this repo.

Extension is via extension points / interfaces / bean registration. **Contracts, not inheritance.**
**Opt-in by default:** nothing is enabled via blanket component-scan.

## Principles (load-bearing)

1. Determinism in the business-logic core (money/claim/state machine). AI only on fuzzy edges.
2. Extract, don't predict: generalize from actual usage, not up front.
3. Simplicity over flexibility: single-tenant, modular monolith, one Postgres.
4. i18n out of the box (static RU+RO in the MVP).
5. Reproducibility: pin versions, sources-log for non-trivial decisions.

## Code conventions

Full set — in `docs/conventions.md`. Machine enforcement: Spotless + google-java-format +
Checkstyle, gated in CI before merge to dev. We don't rely on memory — tooling guarantees format.

- Package: `io.github.degdev.engine.*` (groupId `io.github.degdev`, verified namespace via the
  GitHub account DegDev — free). "platform" lives in the repo name, NOT in packages.
- Standard Java naming: PascalCase types, camelCase members, UPPER_SNAKE_CASE constants.
- Constructor injection. package-by-feature. Clear DTO/entity boundaries.
- DB table naming: `entity_extra_fields` (entity first), avoid SQL-reserved names, `varchar` over
  native Postgres enums — full rules in `docs/conventions.md`.

## Package structure — package-by-feature (CRITICAL, enforced)

Top-level packages inside `engine.<module>` reflect CAPABILITIES, never technical layers.

ALLOWED: feature packages — `crypto/`, `settings/`, `i18n/`, `persistence/`, `access/`, etc.
Each owns its config, entities, services, and (once it has a web layer) controllers.

FORBIDDEN at module root: `config/`, `util/`, `entity/`, `service/`, `repository/`, `dto/`
as layer buckets. A capability's config lives WITH the capability (e.g. I18nConfig in i18n/),
never in a shared config/ bucket.

- Default to package-private; widen to public only when another package genuinely needs the type.
  (A public engine defines its API surface deliberately — internal helpers stay package-private,
  e.g. GcmCipher is package-private, only CryptoService is public.)
- When a feature grows a web layer (milestone 03+), use layers INSIDE the feature
  (web/ service/ domain/) — "package by layered feature": capabilities outside, layers inside.
- A feature should ideally be deletable by removing its directory.

Rationale + sources in docs/sources-log.md (javapractices, Expedia/Sahibinden: package-private
encapsulation; proandroiddev: layered-feature hybrid). This is a hard convention — placing a class
in a layer bucket because it's "easier" is a defect; put it in its feature.

## Secrets — never hardcode keys (CRITICAL, enforced)

NO key, secret, password, or token value is EVER written into a committed file —
not in source, not in resources, not in test resources, not in YAML/properties,
not in a Javadoc example. This includes the crypto master key, bank credentials,
Telegram/SMS tokens, JWT secrets — any credential.

ALLOWED in committed files: the NAME of a property (`nobilis.crypto.master-key`),
env-var placeholders (`${NOBILIS_CRYPTO_MASTER_KEY}`), and clearly-fake structural
samples in *.example files.

FORBIDDEN: a real or real-shaped value after `=` or `:` for any key/secret/password/
token, in ANY committed file including test resources. Tests that need a key generate
a fresh one at runtime (e.g. @DynamicPropertySource), never read it from a committed file.

This is not a discipline rule — it's gated. A pre-commit hook + CI secret-scan
(gitleaks) blocks any commit/merge carrying a secret-shaped value. A key in a file is
a defect even if the build is green.

## IP / clean-room (important — public open-source)

- Not a single line from third-party / former private repositories. Written from scratch, from
  understanding + public sources.
- **sources-log:** for every non-trivial decision — the public pattern/standard/doc it derives from.
- Git history from zero. Apache-2.0 license.
- **`.java` header = full Apache-2.0 header with the copyright line verbatim:
  `Copyright (c) 2026 Dmitri Puscas (DegDev)`** (year = the file's creation year; currently 2026).
  This exact wording is repo-local and overrides any global header rule. Never apply a Compo
  header; decline any request about a Compo template/structure/names.

## Working method

- recon → spec → tasks → DoD. Milestone plans live in `.agent/plans/` (00-foundation … 07-domain-slice).
- Before coding a milestone — a recon ticket: close remaining TBDs, record them in sources-log.
- Each task is atomic and verifiable against its DoD.

## Working principles

How the agent works on every task — independent of the prompt's wording.

- **Think before coding.** State assumptions; if a request has more than one reading, surface the
  options instead of silently picking one. If a simpler approach exists, say so. When something is
  unclear, **STOP**, name exactly what's unclear, and ask — don't guess.
- **Simplicity first.** The minimum code that solves the task. No speculative features, abstractions,
  configurability, or error handling for impossible cases that weren't asked for.
- **Surgical changes.** Touch only what the task needs. Don't reformat, rename, or "improve"
  neighbouring code; match the existing style even where you'd do it differently. Each changed line
  must trace back to the request.
- **Goal-driven.** Turn the task into a verifiable success criterion and loop until it's met
  ("add validation" → write tests for bad input, then make them pass).

## Commit gate

The agent does **not** commit on its own. Finishing a task = **STOP + a short report** (files
touched, result, what was verified) **+ a proposed commit message**. The user reviews and commits.
This holds even when a specific prompt doesn't restate it; build/docs/fix prompts that repeat a
"commit gate" line are only echoing this rule.

## Branch discipline (CRITICAL, enforced)

One branch per milestone (`01-common`, `02-auth`, `03-app-admin-shell`, ...), branched from
`main`. **`main` is NEVER pushed to directly — all integration into `main` happens via a
GitHub Pull Request (PR), even for a single-commit change.** Docs commits ride on whatever
branch is current — universal, not milestone-locked.

**Before running `git commit` for ANY reason**, verify the current branch matches the
milestone/task actually being worked on by running `git branch --show-current`.
- If the current branch does NOT match what's being worked on — **STOP. Do not commit.**
  Report the mismatch, ask whether to switch or whether it's intentional.
- **Never `git push origin main`.** If work needs to land on `main`, open a PR from the
  current branch and stop — the user merges it (or explicitly asks the agent to via `gh pr
  merge`).

**On the remote:** merge PRs via "Squash and merge" or "Rebase and merge", NOT the default
"Merge pull request" — the latter creates a merge commit that breaks linear history.

**Upstream tracking — a feature branch tracks its namesake, NEVER `main`.**
- A feature branch's upstream is ALWAYS its namesake on `origin` (`origin/<same-branch-name>`),
  NEVER `main` or any integration branch. First push: `git push -u origin <branch>`.
- When creating a branch FROM main (`git checkout -b <new> main`), do NOT inherit main's
  tracking. The new branch must track `origin/<new>` (created on first push), not `origin/main`.
  If `git status` shows the new branch tracking main/an integration branch, that's a
  misconfiguration — fix with `git branch --unset-upstream` then `git push -u origin <branch>`.
- **NEVER `git push` while a feature branch's upstream points at main/an integration branch** —
  that pushes your commits straight into it, bypassing the PR gate. Verify with
  `git rev-parse --abbrev-ref @{upstream}` if unsure; it must read `origin/<same-name>`.
- Integration into `main` is via PR only (see the PR-only rule above); the upstream link never
  changes that.

## MCP servers — mandatory usage

This project runs Claude Code from VSCode against BOTH repos (`nobilis-platform-back`,
`nobilis-platform-front`) with IntelliJ IDEA open. The following MCP servers are connected and their
use is **required**, not optional. Do not fall back to plain text search or training-memory when an
MCP server covers the need.

### jetbrains — REQUIRED for all code navigation and inspection
You MUST use the `jetbrains` MCP server (the IntelliJ IDEA index) to read, navigate, and understand
the codebase — across BOTH the backend and frontend repos. Do not rely on plain `grep`/file globbing
or on memory of where things are when jetbrains can answer it.

- Before editing or referencing any symbol, resolve it through jetbrains (find the class/method/file,
  its definition, and its usages) instead of guessing its location or signature.
- To check the impact of a change, use jetbrains to find usages/references — not a text search.
- For multi-module structure (Maven modules, the dependency graph, Angular projects), query jetbrains
  rather than inferring from paths.
- Use IDEA's inspections/diagnostics via jetbrains to catch problems the build alone may not surface.
- If jetbrains is unavailable or returns nothing for a query, say so explicitly, then fall back —
  do not silently skip it.

This is a hard rule: jetbrains is the primary way to read this codebase. Skipping it and answering
from memory or raw text search is a defect.

### context7 — REQUIRED before using any library/framework API
The stack is deliberately newest-LTS (Java 25, Spring Boot 4.1, Angular 22, Hibernate 7, Flyway,
Postgres 18). Training memory for these is stale or wrong. Before writing code against a library or
framework API, you MUST consult `context7` for the current, version-correct documentation — do not
write the API from memory. This applies especially to: Spring Boot 4 / Spring Framework 7
configuration, Hibernate 7 (`@UuidGenerator`, JPA auditing), JCA crypto (`AES/GCM/NoPadding`), Flyway
config, and Angular 22 (signals, Signal Forms, zoneless, `httpResource`).

### playwright — UI verification, ONLY from milestone 03 onward
`playwright` is for verifying real UI in a running browser. It does NOT apply yet: through milestone
`01-common` and `02-auth` there are no screens. The frontend work in `01` is a locale service + an
HTTP wrapper skeleton (no rendered UI) — verify those with unit tests (Vitest + `HttpTestingController`)
and `ng build`, NOT with playwright.

From milestone `03` (admin shell + first screens) onward, every frontend task that produces or changes
UI MUST end with a playwright verification: navigate the running app, exercise the change, inspect the
rendered DOM + actual network requests + console errors. Type-check/build passing is NOT sufficient
for UI work — prove it in the browser via playwright. State plainly what was verified vs. what couldn't be.

### Cross-repo execution
A single Claude Code session works on both repos. For fullstack milestones, do the backend part in
`nobilis-platform-back` and the frontend part in `nobilis-platform-front`, following the paired plan
files (same `<feature-id>-<slug>.md` name in each, mutually linked). Sibling repo paths come from
`.claude/local-config.json` (`backend_path` / `frontend_path`).
