# Architecture backlog

Requirements/ideas needing alignment before implementation — worked through per
`docs/process/prompting-methodology.md` → Type 6 (ARCHITECTURAL DISCUSSION → BACKLOG). Not a task
list; a holding area for design questions until a real trigger resolves them.

---

## BL-001 — App-module extension points (domain hook mechanism)

**Status:** To align (deferred, not designed)

**Context:** Milestone 03 (`app-admin-shell`) originally scoped an "extension points" mechanism in
the `app` module, letting the domain layer (milestone 07, `homeservice-*`) hook into the portal
shell (e.g. the order-submission form). The 03-app-host-boot recon (2026-07) found this prose
already living ahead of code in `app/pom.xml` and `app/package-info.java` — no interface, SPI, or
documented contract exists — and reclassified it here rather than building it now.

**Problem:** Designing a generic extension mechanism today would be built against exactly ONE named
future consumer (the milestone-07 order form), which doesn't exist yet. That's the textbook
"infrastructure built speculatively is a defect" case (extract-don't-predict; the project's own
`[ready]` threshold requires 2+ real examples before extracting a pattern/skill). Guessing the
contract shape now all but guarantees a rework once the real consumer shows up.

**Idea (not locked, just the current guess):** likely something in the family of a
`@ConditionalOnBean`-gated extension-point interface + auto-config, mirroring the engine's existing
mounting pattern (`docs/playbooks/engine-screen-mounting.md`) — but this is a guess, not a decision;
do not build from this paragraph.

**Open questions (resolve when the trigger below fires, not before):**
- What does the domain layer actually need to inject — a full route, a component slot, a data
  provider, an event hook? Unknown until the order-form recon (milestone 07) states it concretely.
- Is one consumer enough to design from, or does the `[ready]` 2-example threshold mean waiting for
  a second domain consumer before extracting the mechanism at all?

**Scope:** Engine (`app` module) — no domain code.

**Decision needed by:** Not urgent — resolves naturally when milestone 07's order-form recon reaches
the "how does this attach to the app portal" question. Revisit this entry then, not before.

---

## BL-002 — CMS image/MinIO attachment

**Status:** To align (deferred, not designed)

**Context:** Milestone `03-cms-screen` added a generic content-block mechanism (`ContentBlock` +
`ContentTranslation`, text only). MinIO exists today only as an unwired docker-compose service
(`stack.yml:77-93`) — zero SDK dependency, zero Java client, zero consumer anywhere in either repo.
Wiring an S3/MinIO SDK into the first CMS pass would be infrastructure-ahead-of-need.

**Problem:** No concrete consumer has stated what an image attachment needs yet (single hero image?
gallery? per-translation images?) — designing the storage contract now would guess at that shape.

**Idea (not locked, just the current guess):** a sibling entity/column on `ContentBlock` or
`ContentTranslation` holding a MinIO object key, resolved to a URL at read time — but this is a
guess, not a decision; do not build from this paragraph.

**Scope:** `common` (CMS entities/service) — no domain code.

**Decision needed by:** Not urgent — resolves when a real screen (admin CMS editor, milestone 03
pass 4, or a domain consumer) states a concrete image requirement. Revisit this entry then, not
before.
