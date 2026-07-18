# Plan: 07-admin-design — sakai-ng layout shell (backend anchor)

## Feature ID
`07-admin-design`

## Scope
Fullstack — **backend part**. This milestone is **frontend-only**; this file is a thin anchor for
provenance/backlog bookkeeping, not a backend implementation plan. No backend module gets code,
API, entity, or migration changes as part of this milestone.

**Paired plan:** `07-admin-design.md` in the frontend repo (`nobilis-platform-front`) — the real
plan; all decisions, slices, and architecture live there.

## Applicable playbook
N/A — no backend work.

## Goal
Track the one backend-repo touch this milestone makes (a docs update, not code) so it isn't lost
between repos, and give the frontend plan a paired file per the fullstack plan convention.

## Architectural decisions
None — this is a frontend-only milestone (sakai-ng layout shell fork into the front `common`
library). See the frontend plan for the full decision set.

## Files to create / change

### Backend
- `docs/architecture-backlog.md` — `BL-004` ("Admin nav as data") status update, from `To align`
  to resolved/decided, referencing the frontend plan's slice 3 (typed nav-model contract). This
  edit lands as part of the **frontend** slice 3 commit (cross-repo commit pair on the same nav-model
  change), not as an independent backend task — recorded here so the backend repo's history explains
  why an unrelated-looking milestone touched this file.

### Frontend
See `07-admin-design.md` in `nobilis-platform-front` for the full file list.

## Open questions
None on the backend side.

## Testing strategy

### Backend
N/A — no backend code changes.

### Frontend
See the frontend plan.

## Related features
- Cross-repo: paired primary plan `07-admin-design.md` in `nobilis-platform-front`.
- Resolves `BL-004` (this repo's `docs/architecture-backlog.md`) — status update only, no design
  work happens in this repo.
- Does not touch `BL-005` (production same-origin routing) — separate deployment track, still
  deferred.

## Risks
None on the backend side — see the frontend plan's risk section (submodule/license surface,
configurator rewrite size, PrimeNG/Angular peer gap, cross-repo commit hygiene in slice 3).
