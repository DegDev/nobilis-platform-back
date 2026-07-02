# Playbooks — nobilis-platform-back

Documented project patterns for CLASSES of tasks. The `/plan` command reads this index as its
**first step** and picks the matching playbook — planning and implementation then follow the
established pattern instead of re-explaining it each time (previously such patterns were scattered
across CLAUDE.md / memory / ad-hoc prompts).

Index line format: `` - `<name>.md` — <when to apply (trigger / task class)> [status] ``

## Principle: extract, don't predict

A playbook is written **after** the first real example, not before. An empty playbook written on
guesses will need rewriting after the first implementation. Everything below is **[anticipated]** —
the task class we expect to recur — and becomes **[ready]** only once we've built one real instance
and captured the pattern from it. (The first `[ready]` one, `lombok-conventions.md`, was extracted
from the 01-common / 02-auth code — not predicted; the rest stay `[anticipated]` until built.)

## Paired (fullstack) playbooks

Fullstack playbooks are split into back/front parts so the two repos don't drift through
duplication. The frontend parts live in `nobilis-platform-front/docs/playbooks/` with a mutual link
inside each file. The contract (DTOs, endpoints) is the seam between the pair.

## Backend playbooks (ready)

- `lombok-conventions.md` — before creating/editing any data class, DTO, entity, or service: which
  Lombok annotations (and when a `record` or a hand-written constructor is right instead). Backend
  only (Lombok is JVM-only). **[ready]**

## Backend playbooks (anticipated)

- `crud-standard-backend.md` — bring a reference-data resource to the standard: entity +
  repository + the CRUD endpoints registered on the admin CRUD framework. Fullstack pair in front:
  `crud-standard-frontend.md`. **[anticipated — milestone 03]**
- `import-export-roundtrip-backend.md` — a resource's export is a dedicated `…/export` in the
  **import format** (round-trip), not a generic "dump the table to a sheet". Defines the format
  contract. Fullstack pair in front: `import-export-roundtrip-frontend.md`.
  **[anticipated — milestone 05/07]**
- `async-consumer.md` — a Kafka consumer in the integration worker: topic → handler → DLQ topic on
  failure (no built-in requeue in Kafka). The template for all async work (notifications,
  moderation, LLM jobs). **[anticipated — milestone 04]**
- `notification-dispatch.md` — event → template (locale) → transport (email / SMS / Telegram),
  driven by the generic dispatcher. **[anticipated — milestone 04]**
- `integration-adapter.md` — an external-service adapter (request, signing, reconciliation via
  polling, never trusting a callback as authoritative). The bank adapter is the first real
  instance. **[anticipated — milestone 07]**

## Cross-cutting / process playbooks (anticipated)

- `feature-optionality.md` (back part) — opt-in feature wiring; a capability is **off until the
  host explicitly enables it** (no blanket component-scan), matching the engine opt-in principle.
  Paired front part in `nobilis-platform-front/docs/playbooks/`. **[anticipated]**
- `duplicate-detection.md` — find duplicated code before extracting it to a shared place; extract
  by agreement, EXCEPT in hot/performance-critical paths (keep those separate). Two-sided (Java and
  TS); this is the primary file. **[anticipated]**
- `recon-first.md` — recon before touching coupled or risky code; refactor hot code as a separate,
  tested task, never as a drive-by. Process playbook, both sides. **[anticipated]**

> The remaining entries are anticipated classes, not yet written — each captured from the first real
> feature that exercises it, not predicted here. The first captured one is `lombok-conventions.md`
> above, extracted from the 01-common / 02-auth code.
