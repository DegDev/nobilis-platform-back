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
