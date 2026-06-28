---
description: Create a feature plan in .agent/plans/ before implementation. Do not write code.
---

Create a feature plan. Do not write code.

The plan form follows the repo methodology: **recon → spec → tasks → DoD** (as in
`.agent/plans/00-foundation.md` onward). Don't confuse it with the one-off Stage 1 milestones —
this command is for NEW features that don't yet have a written plan.

## Steps

**STEP 0 — pick a playbook.** Read this repo's `docs/playbooks/README.md` and pick the playbook
matching the task class — follow it while planning, without re-explaining the pattern. If there are
no playbooks yet or none fit — note that explicitly in the "Applicable playbook" section and
continue (playbooks fill in as real patterns emerge: CRUD screen, import-export, etc.).

1. **Check the directory** `.agent/plans/`. If missing — create it: `mkdir -p .agent/plans`.

2. **Ask the user** (if not given in the request):
    - **Scope**: backend / frontend / fullstack
    - **Feature ID**: e.g. `NB-001`
    - **Slug**: kebab-case description (e.g. `entity-translation`)
    - **Context from web discussion** (optional): if there's a detailed prompt with architectural
      analysis — use it as the source for the "Architectural decisions" section.

3. **For fullstack / cross-repo** — find the sibling repo path:
    - Read `.claude/local-config.json` if present.
    - If there's a `frontend_path` (when in back) or `backend_path` (when in front) — use it.
    - If not — ask the user for the path and offer to save it in `.claude/local-config.json` for later.
    - During Stage 1 the front may still be empty — then plan single-repo (backend only) and note
      that the front part will be added later.

4. **Create the plan file(s)**:
    - **backend only**: `.agent/plans/<feature-id>-<slug>.md` in this repo (nobilis-platform-back).
    - **frontend only**: `.agent/plans/<feature-id>-<slug>.md` in the front repo (nobilis-platform-front).
    - **fullstack**: two files with the same name `<feature-id>-<slug>.md`, one per repo, each with
      its part and a mutual "Paired plan" link.

5. **Plan structure** (markdown):

```markdown
# Plan: <title>

## Feature ID
<e.g. NB-001>

## Scope
<backend | frontend | fullstack — backend part | fullstack — frontend part>

For fullstack:
**Paired plan:** `<feature-id>-<slug>.md` in the <back|front> repo.

## Applicable playbook
- `docs/playbooks/<name>.md` — <why it fits the task class>
  (or: "no matching playbook — new pattern; capture it in playbooks after implementation")

## Recon (before code)
- Remaining TBDs to close before implementation.
- What to verify/read (standards, docs, existing engine code).
- Decisions → record in `docs/sources-log.md` (provenance, clean-room).

## Decisions (user-confirmed)
<Decisions the user has explicitly ratified — kept separate from open Recon TBDs so settled
questions aren't relitigated. Number them.>
1. <decision> — <one-line rationale>

## Goal
<one sentence: what we do and why>

## Architectural decisions
<Key choices and rationale. If there was a web analysis — from there. Keep within the engine/domain
boundary: this repo is engine only (capabilities); domain specifics go in the homeservice repo.
For front scope, the UI library is PrimeNG (from milestone 03).>

## Spec — files to create / change

### Backend (if scope includes backend):
Engine modules: `common`, `ai`, `auth`, `app`, `admin`, `integration`.
Dependencies strictly one-directional: `common ← {ai, auth} ← {app, admin, integration}`.

- `<module>/src/main/java/io/github/degdev/engine/<module>/...` — description
- DB migrations (if needed): `common/src/main/resources/db/migration/V<N>__<name>.sql` (Flyway,
  SQL-first; Postgres-specific SQL is fine — we nail Postgres, not abstract it)

### Frontend (if scope includes frontend):
Angular workspace: `common` (lib), `admin` (app), `app` (app).

- `projects/<project>/src/...` — description

## Tasks (atomic units)
1. ...
2. ...
<each task atomic and verifiable>

## Testing strategy
### Backend (if applicable):
- Unit (JUnit 5)
- Integration — if integrations are touched (Kafka, DB, external services)
### Frontend (if applicable):
- Unit (Vitest + Angular TestBed)
- Component / E2E — if applicable

## DoD (verifiable readiness criteria)
- <concrete, measurable conditions: "mvn verify green", "opt-in: builds without X",
  "claim race test — two actors, one wins", etc.>
- Provenance: each non-trivial decision recorded in `docs/sources-log.md` (clean-room).

## Open questions
1. ...

## Related features / dependencies
- Cross-repo: paired plan (for fullstack)
- Blocking features / build-plan milestones

## Risks
- ...
```

6. **Show a summary** of the created file(s). Do not write code.

For trivial tasks (typo, rename) — ask whether a plan is needed at all, or you can just do it.
