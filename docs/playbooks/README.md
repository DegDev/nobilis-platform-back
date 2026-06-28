# Playbooks

Repeatable task recipes for the engine — patterns we've worked out once on a live example and want
to reproduce uniformly (Claude Code follows them instead of re-inventing).

## Principle: extract, don't predict

A playbook is written **after** the first real example, not before. An empty playbook written on
guesses will need rewriting after the first implementation. So this folder fills up as patterns
emerge, not in advance.

## When each playbook is likely to appear (guideline)

- **CRUD screen** — at milestone `03` (CRUD framework) or `07` (first domain CRUD): build one screen
  properly → capture the pattern here → then stamp out the rest by it.
- **Notification (event → template → transport)** — at milestone `04`.
- **Async consumer on the bus** — at milestone `04`/`06`.
- **i18n for a new field/screen** — at milestone `05`.
- **Integration adapter** (external service, signing, reconciliation) — at milestone `07` (bank) as the exemplar.

## Format

Each playbook is a separate `<name>.md`: the task class, steps, files/modules, common pitfalls,
readiness checklist. The `/plan` command reads this README and picks the matching one.

> Empty for now — that's expected. The first playbook appears at milestone `03`.
