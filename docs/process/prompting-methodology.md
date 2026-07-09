# Prompting Methodology — Recon-First (for AI coding agents)

> A reusable, project-agnostic methodology for writing prompts to AI coding agents (Claude Code and similar). Distilled from hands-on practice. Language-, stack-, and domain-neutral. **This is a living document — extend and refine it as new patterns prove out.**

## How any chat should use this

When the user asks to **create a prompt** for an AI coding agent (recon, implementation, a rule, a new feature, a bug hunt, an architecture decision), follow the principles and the matching prompt type below by default. This is the baseline, not a straitjacket: adapt to the situation, and when the user establishes a new durable pattern, **add it here** rather than letting it evaporate in one chat. If a project has its own conventions doc, the project's specifics win; this provides the universal scaffold.

---

## Two-track workflow (by task weight)

Not every task earns a full recon. To avoid over-engineering trivia and under-estimating complexity, tasks split into two tracks. The assistant picks the track itself by task weight and names it in one line; the user can override explicitly at any time.

**Light track** — cosmetics, renames, small UI tweaks, changes with an obvious entry point.
- Go straight to a BUILD prompt with an inline gate (a single STOP/verify check inside), no separate recon phase.
- No barrage of clarifying questions — make reasonable default assumptions and flag each in one line.
- Ask one short question only if a fork genuinely changes the result (a wrong default = rework).

**Heavy track** — architecture, changes at a protected boundary, new pipelines, unknown integration points.
- Recon-first as usual (read-only investigation before design).
- Recon is justified when not knowing the spot risks rework. Typical saves: a base-class/dependency operation that silently doesn't work, a filter that conflicts with another predicate, a refresh/reset action that turns out destructive, an enum storage model (ordinal vs string) that bites later.

**Rules for both tracks:**
- **Track choice is the user's** if stated explicitly. If not stated, the assistant picks by weight and names it in one line.
- **Clarifying questions:** one short question only on a genuinely ambiguous fork where a wrong default = rework — not on every minor decision. One short question on the contentious point beats a silent wrong default.
- **Chat answers are dense:** decision + brief "why", not an essay.
- **Recon is not ceremony:** only when it pays off. Something trivial (a one-row DB UPDATE, a rename) may not be an agent task at all but a one-off operation — say so plainly instead of wrapping it in a prompt.

---

## Quick checklist — 10 principles for writing any agent prompt

The condensed rule the assistant follows when authoring a prompt (the sections below expand each):

1. **Type drives structure.** Every prompt is one of: RECON (read-only) / BUILD (implement) / FIX (bug) / DOCS (docs). Declare the type in the header; it dictates which sections are needed.
2. **Header sets the frame.** Task/ticket, branch, mode, commit-gate reminder (the agent doesn't commit itself — it ends with STOP + report + proposed commit message), which tools to use explicitly (the agent ignores them by default), cross-cutting constraints (read-only zones, conventions, package structure).
3. **GATE-0 — mandatory check before code.** Before any implementation the agent confirms key points (signatures, existing patterns, constraints) and STOPS with a verdict if the task hits the impossible/unsafe. Catches a wrong mental model before files are written. The gate pays off — it has repeatedly caught unworkable tasks (can't write a read-only field; a method needs something that doesn't exist).
4. **Context marked "recon-confirmed — do not re-verify."** What's already scouted is given as fact with a file:line reference, so the agent doesn't spend passes re-investigating. Separate the known from what the agent must find itself.
5. **Name known traps in the prompt.** Pattern conflict, side-effect of a past fix, a fragile spot — write it into context so the agent doesn't step on it. Evidence over impressions: a trap with proof, not "careful, maybe."
6. **Forks inside the GATE, with an explicit STOP.** Architectural choices (where to fix, which approach) are decided in the gate with rationale, stopping before code — not silently mid-implementation. Lock the decision before building.
7. **DoD is measurable, with bug reproduction.** Definition of Done = concrete checks, not "works." For a fix: a reproduce->fix test (the specific failing case now passes). Documentation in the DoD if the task needs it.
8. **One defect / one topic per pass.** Don't mix a correctness fix with an optimization, don't bundle unrelated edits. Each pass = its own commit, its own verifiability. Announce a series explicitly ("this is 1 of N").
9. **Out-of-scope is explicit.** At the end: what this prompt does NOT touch (deferred, other passes). Guards against scope creep.
10. **Task weight -> prompt depth.** Cosmetics: straight to BUILD with GATE-0 inside, no separate RECON. Architecture / unknown territory: RECON-first. Don't over-engineer trivia with ceremony. (See the two-track section above.)

---

## General principles (apply to ALL prompt types)

1. **Recon-first.** Almost any non-trivial task begins with read-only investigation. Don't design or implement blind — first uncover the actual state of the code. Motto: *recon first*.
2. **STOP-points.** A prompt explicitly states where the agent must STOP and show results (after recon, after a phase, at a commit-point). Never "do it all at once."
3. **Evidence, not impressions.** Recon must return concrete proof (file paths, class names, DB rows, version-control history), not vague "it seems to work like X."
4. **Empirical verification over guessing.** Prefer checking against the live system (a real database, a running app, a browser) over decompiling artifacts or assuming. A recurring lesson: a decompiled/cached artifact gave the WRONG answer while the live source gave the truth — verify at the source.
5. **Respect declared boundaries.** If part of the system is off-limits (read-only dependency, generated code, another team's module), state it explicitly in the prompt and verify the boundary isn't crossed. New work extends across the boundary, it doesn't edit through it.
6. **Honor the project's optionality model.** If the project gates features (flags, kill-switches, opt-in config), keep new work consistent with it — typically off by default. Don't silently turn things on.
7. **Name the known traps.** List the project's known pitfalls in the prompt (a case-sensitivity quirk, an async timing gap, a config-vs-annotation gotcha, a coverage hole). The agent is warned in advance, beside the place the trap fires.
8. **No false urgency.** Keep tone calm. No artificial deadlines. The user manages their own time.
9. **Keep a verify phase** in the prompt even when the user verifies personally — a double guarantee.
10. **Skepticism toward AI-proposed variants.** An AI's options "A/B/C" are the statistical top, not an exhaustive enumeration — the air of completeness is false. Before picking among them, widen the space: ask "what less obvious options weren't considered?" and "what fits this project's specifics even if atypical?" — treating the AI's answer to *that* as another top-probable list, not proof (the human owns final completeness, not the agreeing AI). A variant the *user* introduced earns the same skepticism: the AI's later "support" for it is an artifact of context + agreeableness, not validation. Cross-cutting, sharpest at recon (gathering options) and architecture -> backlog (choosing an approach) — at any STOP-point returning variants, widen-then-guard before locking.

---

## Context hygiene between passes

Long sessions degrade as the window fills; keep it clean deliberately. Agent-neutral principles (Claude Code commands noted in parentheses).

- **Clear context between passes** (`/clear`). One pass = one topic = one commit. After a pass reaches its commit-gate (STOP + report), clear before starting the next unrelated pass — don't carry a finished topic into the next. A bloated window dilutes attention on every later instruction.
- **Sidebar tangents out of the working thread** (`/btw`, or a throwaway session). A quick question that shouldn't enter the task history goes to the side, so the main thread stays on the task.
- **Focused compaction for long single-topic stretches** (`/compact <focus>`). When one pass legitimately runs long (a hard bug, a big feature), compact with an explicit focus line to preserve the thread being worked while shedding the rest — better than letting an unfocused auto-compact decide.
- **Investigation stays in a subagent.** Multi-file recon runs in the recon subagent and returns a compact file:line summary marked do-not-re-verify; the raw exploration never lands in the main window. (Already standard — restated here as the primary context-budget lever.)

---

## Type 1 — RECON (read-only investigation)

**When:** before implementing, diagnosing a bug, designing, or any change to unfamiliar territory.
**Goal:** uncover the ACTUAL state of the code, with evidence. Change nothing.

    HEADER: RECON (LAYER: BACK/FRONT) — <what to investigate>. READ-ONLY, STOP with a diagnosis, do NOT fix.

    ## Clue / Context
    - concrete symptoms (endpoint, behavior, what arrives / doesn't)
    - what is ALREADY known (from prior recon, from memory)
    - hypothesis of the problem class, if any: "data exists, render doesn't show it" / "source mismatch"

    ## Definitions (if the task involves classification — embed them, for unambiguity)
    - the terms the agent will classify by, with the markers of each

    ## RECON: (numbered lines of investigation)
    1. <what to look for> — concrete files/classes/tables, exactly what to check
    2. <branching> — if X, check A; if Y, check B
    3. <data source> — where it originates, where it's lost
    4. <regression/history> — version-control blame: when it broke/appeared (to find the SPOT, not the culprit)
    5. <boundaries> — which side of the off-limits boundary

    ## Premise check (do this FIRST, before the rest of the report)
    Mechanically verify each concrete claim the prompt makes against the code — this is NOT "judge
    whether the premise sounds plausible" (a judgment call), it's checking specific assertions:
    - prompt says "X is stored/formatted as Y" -> find the actual mapping/type in code
    - prompt says "X returns/does Y" -> find the actual handler
    - prompt assumes a method/field/endpoint exists -> confirm via search, not assumption
    - the task requires editing across a declared boundary -> confirm it's actually impossible as described
    A mismatch on any of these is reported FIRST, before proceeding — don't bend the rest of the
    report to fit a false premise.

    ## Recon report: (what to return)
    - Premise check: confirmed / WRONG (which claim, file:line) — first
    - WHY <problem> — possible causes, distinguished from one another
    - WHERE to fix
    - which side of the boundary
    - evidence (paths/classes/lines)
    - doc drift, if any (docs describe how it SHOULD be; code is how it IS — note if a cited doc is stale)

    STOP with a diagnosis. Fix comes separately.

**Key recon techniques:**
- **Distinguish causes:** not "find the cause" but "the cause is A / B / C — determine which."
- **Name the problem class:** if a pattern is visible ("data arrives, the front doesn't render it"), name it; recon confirms or refutes.
- **Version control for the SPOT, not blame:** "when did the mapping drop out" — to restore it, not to accuse. Whose regression is irrelevant; it needs fixing.
- **Investigate-along-the-way:** if recon touches a component needed later, scout it too while you're there.
- **Self-correct via the live source:** explicitly encourage checking the DB/runtime when a guess or a stale artifact might lie.
- **Collect facts symmetrically — recon does NOT pick a winner.** When the question has options (approach A vs B), recon reports, for EACH option, what existing code would support it and what blocks it — facts only, no "the code leans toward A", no recommendation. "Leans toward" is already design, and design is a separate pass. A recon that nudges the answer has stopped being recon. The choice happens after the report, with the facts in hand.
- **Premise check — recon may correct the question, not just answer it, and this is MECHANICAL not a judgment call.** A prompt's stated context can be wrong (a premise built on a stale assumption — e.g. "opt-in is wired via X" when the code does Y, or "enum stored as ordinal" when it's a string). Frame the check as verifying specific, concrete claims against the code (a type, a return value, an existence assumption) — NOT as "does this premise sound plausible" (plausibility judgment is a weaker fit for fast/cheap models; mechanical verification against file:line is a strong fit). If recon shows the premise is false, say so plainly BEFORE the structured findings — do not bend the answer to fit the question. Verify referenced rules/principles actually hold in code rather than taking the docs on faith (docs drift from code — report drift explicitly when found). Recon that blindly executes a false premise produces confident wrong work.
- **Fast/cheap models are a legitimate choice for recon** (evidence-gathering doesn't need the strongest model) PROVIDED the rules compensate: turn judgment calls into mechanical checklists (premise check above), require file:line for every claim, and keep the agent from ever drafting a fix. If a fast-model recon starts producing shallow or wrong reports, that's a signal a rule needs to become more mechanical, not that recon should carry more free judgment.
- **Decisive-first ordering.** When recon already has a strong hypothesis about the cause (especially one the prompt's author already stated in the header), put the cheapest test that would decisively confirm or refute that hypothesis FIRST in the investigation order, not last. If it confirms, the remaining lines are often redundant or read differently in its light. (Marked illustration: a bug reproduces on one environment and not another → compare environment state as the first line, not the last.)

---

## Type 2 — IMPLEMENTATION (after recon)

**When:** recon done, forks decided, pattern/boundaries/traps known.
**Goal:** implement against a checklist, in phases, with commit-points.

    HEADER: IMPLEMENTATION — <feature>. Following <reference pattern>. STOP at commit-points.

    ## Locked decisions (from the post-recon discussion)
    1. <decision on fork 1>
    2. <decision on fork 2>
    ... (a NUMBERED list of ALL decisions, so the agent doesn't re-ask or decide on its own)

    ## <Data format / columns / contract> (if applicable)
    - exact spec (columns, DTO fields, value format)
    - TRAP warnings inline in the spec (case, required-ness, defaults)

    ## BACKEND
    - component 1 (what to copy-adapt, what to reuse as-is)
    - component 2 ...
    - traps inline (config-vs-annotation; broken row -> null filter; upsert by <key>)
    - what NOT to copy (use the existing generic)

    ## FRONTEND
    - component, route, reuse of generic dialogs
    - the wiring pattern

    ## <Round-trip / invariants> (if applicable)
    - what must hold (export = import symmetry, etc.)

    ## TESTS
    - scenarios (happy path / upsert / broken data / edge / case / Unicode / NON-deletion)

    ## DoD (Definition of Done)
    1. code implemented
    2. tests green (back + front)
    3. DOCUMENTATION (dev-doc + a live example in the playbook) — MANDATORY, not optional
    4. declared boundaries respected
    5. restart/rebuild — done by the user manually

    ## OPEN (if minor forks remain)
    - question + recommendation (the agent applies it or asks)

    STOP after <BACKEND at the commit-point> -> then FRONTEND. Show the plan before code.

**Key implementation techniques:**
- **Lock decisions BEFORE code:** every fork from discussion, as a numbered list, so the agent doesn't re-ask or self-decide.
- **Reference pattern:** point at an existing working pattern, not abstract design.
- **Copy-adapt vs as-is:** explicitly distinguish what to reuse unchanged (generic) from what to copy and adapt.
- **Traps in the body:** known pitfalls right next to where they fire.
- **Commit-points between phases:** BACK -> STOP -> FRONT. Not all at once.
- **DoD includes documentation:** documentation is a MANDATORY part of done, not an afterthought (agents systematically skip it -> keep it in the DoD).

---

## Type 3 — RULE / PROCESS (conventions doc, playbook)

**When:** a recurring pattern (explained 2+ times) / an "always/never" rule / the agent repeats a class of error.
**Goal:** record the rule in the project's instructions (local/global) or a playbook, so it isn't repeated.

    HEADER: <CRITICAL> RULE — <essence>. Add to <local/global instructions>. <priority>.

    ## Problem
    - what goes wrong without the rule (concretely, with an example)

    ## RULE (exact text to insert)
    ### <section>
    ALLOWED: ...
    FORBIDDEN: ...
    (or: always X / never Y, with rationale)

    ## Placement
    - LOCAL: the project's instruction files
    - GLOBAL: the cross-project instructions (if it applies to any project)
    - priority (CRITICAL, so it isn't ignored)

    ## DoD
    - rule in the right places
    - clear allowed/forbidden
    - (opt.) a technical barrier stronger than the instruction

    Apply immediately.

**Key rule techniques:**
- **Rule from a PROVEN pattern,** not theory. Verify first, then write it into a playbook.
- **When to write a rule:** a second explanation / an "always/never" / the agent repeats a class of error.
- **Instruction vs barrier:** distinguish a behavioral rule (the agent should follow) from a technical barrier (a read-only token/scope it physically cannot violate). A barrier is stronger than an instruction — propose it where it exists.
- **Local vs global:** project-specific -> local; process-level (applies anywhere) -> global.
- **Exact text to insert:** give the ready block, not "write a rule about X."

---

## Type 4 — NEW FEATURE (recon-first, multi-phase)

**When:** sizeable new functionality.
**Goal:** investigate (domain + pattern + format) -> agree forks -> implement.

    HEADER: NEW FEATURE — <n>. RECON-first (<what to scout>), then implementation. STOP after recon.

    ## Goal (business intent + what to build)

    ## PHASE 1 — RECON (read-only, STOP)
    ### A. Domain model (entities, fields, relations, uniqueness)
    ### B. Existing pattern (what to reuse — the reference)
    ### C. Format/contract (columns, API, structure)
    ### D. Boundaries (which side)
    Recon report: ... STOP.

    ## PHASE 2 — IMPLEMENTATION (after recon + agreeing forks)
    (structure as in Type 2)

    ## PHASE 3 — PLAYBOOK / rule (if the pattern is repeatable)

    ## TESTS / DoD

    STOP after PHASE 1 — we agree the forks, then implement.

**Key new-feature techniques:**
- **Three axes of recon:** domain (what we model) + pattern (what we reuse) + format (the contract).
- **Forks for agreement:** recon ends with OPEN questions (scope / uniqueness key / what's together vs separate) that the user decides BEFORE implementation.
- **Preventive round-trip:** if a format is defined from scratch, design it symmetric (import = export) up front, to avoid redoing it.

---

## Type 5 — BUG DIAGNOSIS (a recon variant)

**When:** something behaves wrong (data missing, not displaying, a contradiction).
**Goal:** find the root + where to fix. Often reveals a problem class wider than the symptom.

    HEADER: RECON — <symptom>. READ-ONLY, STOP with a diagnosis, do NOT fix.

    ## Clue (the contradiction / gap, concretely)
    - what is observed (a counter shows X, but Y is empty)
    - what it means (data exists / the projection doesn't return it)

    ## RECON: (where it's lost)
    1. source (where it comes from)
    2. path (where it passes through)
    3. gap (where it's lost / doesn't arrive)
    4. is this <cause A> or <cause B>?

    ## Report: root + where to fix + class (is it a one-off or wider)

**Key diagnosis techniques:**
- **Problem class:** a frequent pattern ("data exists, display doesn't show it" / "filter source != display source") — name the class, look for it recurring wider.
- **Contradiction as a clue:** "counter says 15, body empty" — an internal contradiction points at the gap.
- **Backlog escalation:** if a bug exposes a root problem (a coverage hole), don't patch under the bug — raise it to the architectural backlog as a separate task.
- **A boundary/independence proof by name-grep alone is incomplete.** Grepping for named classes/tokens/identifiers to prove one module doesn't depend on another misses the silently-inherited baseline layer: bare-tag selectors, resets, global typography, root-level variables, cascading defaults. Check that layer explicitly, alongside the named-token grep, or the "confirmed independent" conclusion may be false. (Precedent: a recon called a stylesheet "100% one module's own chrome" from a class/id-selector grep; the same file also held bare-tag global rules the grep couldn't see — the isolation claim was wrong even though the eventual bug lay elsewhere.)

---

## Type 6 — ARCHITECTURAL DISCUSSION -> BACKLOG

**When:** a requirement/idea needing alignment before implementation.
**Goal:** work it through architecturally, record it in a backlog for sign-off.

    BL-XXX — <n>
    Status: To align / In progress / Approved / Deferred
    Context / Problem / Idea (proposed approach) /
    Open questions (what to clarify, especially with the stakeholder BEFORE designing) /
    Scope / Decision needed by

**Key techniques:**
- **Dependencies explicit:** BL-003 DEPENDS on BL-004 (don't build on a leaky foundation).
- **The one stakeholder question:** isolate the single question that determines scope (inheritance or reference?).
- **Reuse the existing:** what's already there vs what's new.
- **Business tie-in:** where the idea strengthens the business model.
- **The main risk:** the technical risk to verify with recon first.

---

## Type 7 — DOCS (create / edit a doc or playbook)

**When:** writing or extending a markdown doc, playbook, or convention file in the repo.
**Goal:** the doc lands in the right place, matches the repo's canon, and nothing existing is lost.

    HEADER: DOC — <add/create> "<section/doc name>" in <path>. Project-agnostic: NO concrete module/
    file/tech/feature names — only portable rules. Do not commit.

    ## First, check the file's state (the inline gate for DOCS)
    1. If <path> EXISTS — add the section below, aligned to the file's existing style/headers.
       Do NOT overwrite the rest. (view first -> targeted patch, never full rewrite.)
    2. If the file does NOT exist — create it, place the section inside, and match the canon of this
       project's docs/playbooks (look at sibling docs / the index README if any, align headings).
    3. If docs are mirrored to a knowledge base / wiki — make a synchronized copy and mention it.

    ## Content of the section (the EXACT text to insert — not a description)
    ### <Section title>
    <the ready-to-paste block: purpose, when-to-apply, the actual rules/tables/checklist>
    ... (give the literal content the agent should write, fully spelled out)

    ## Requirements for the result (the DoD)
    - Fully project-agnostic (no module/file/tech/feature names).
    - Aligned to THIS project's doc/playbook canon (headers/sections match siblings).
    - Mirror in knowledge/wiki synchronized (if one exists in the project).
    - Don't commit — end with STOP + report + proposed commit message (if the project uses a
      commit-gate); otherwise just save the file and report the path.

**Key DOCS techniques:**
- **view first -> targeted patch, NEVER full rewrite.** Hard lesson: rewriting a doc loses existing content. Edit the smallest region; for existing files, add a section, don't replace the page.
- **Exact text to insert, not a description.** Give the literal ready-to-paste block ("### Section ... <content>"), not "write something about X" — same as the RULE type.
- **Sync mirrors and indexes:** if playbooks/docs are mirrored (wiki, paired back/front, an index README), update every copy and the links between them in the same pass.
- **Match the repo canon:** new docs align to sibling docs' header block + section order, so the doc set doesn't drift (a doc in its own format is the start of structural drift).
- **Project-agnostic by default for process docs:** generalize ("core/overlay", "read-only module", "integration point"); no concrete names — so the doc is portable and clean for a public repo.

---

## Anti-patterns (what NOT to do in prompts)

- NO "do it all at once" without STOP-points.
- NO abstract rules (SOLID, "write clean") — only concrete project facts + tool-signal rules.
- NO implementation without recon on unfamiliar territory.
- NO trusting a decompile/guess when the live source can be checked.
- NO forgetting DoD documentation (agents systematically skip it — keep it in the DoD).
- NO false urgency.
- NO editing through a declared boundary.
- NO leaving forks to the agent's discretion (lock decisions before implementation).

---

## Deterministic gates: hooks, not prose

A commit-gate (block direct commits/pushes to protected branches; feature branches free) and an end-of-turn build check are not advisory prose in the instructions file — they are `PreToolUse`/`Stop` hooks committed to the repo. A rule as text erodes and gets bypassed; a rule as a hook is a deterministic boundary: it parses command chains and the `-C`/`cd`/cwd of the *target* repo to read that repo's branch (not the hook's own cwd), and the end-of-turn check ignores red test-spec noise, gating only application code. Prefer a barrier to an instruction wherever one exists. The *format* of the commit message (not the permission to commit) stays a playbook concern, separate from the gate.

---

## Deployment requirements — a green build ≠ a running process

A recurring root cause: a green `build`/`test` does not prove a deployed process starts and serves. Three linked rules; the parentheses are illustration, not the norm.

**A `## Deployment requirements` section is mandatory for any subsystem with external config.** A subsystem whose runtime depends on settings *outside* the repo code (properties outside version control, an edge/reverse proxy, DB config, a front's env, a dependency's classpath scope) carries, in its *own* canonical doc, a numbered contract table: `# · What · Where it lives · Default if unset · Consequence of omission · Owner (repo/ops)`. It lives next to the fact (the subsystem's canonical doc), not in a shared README; the birds-eye map only indexes it with a pointer line. **The "consequence of omission" column is mandatory** — it is what separates a contract from a useless list. This section is not a duplicate of a security/audit doc: an audit is point-in-time recon, this is a live deployment contract; it *references* the audit, it does not copy it.

**The prod config mechanism is a GATE-0 evidence class.** Before any config- or dependency-edit, establish how prod *actually* loads configuration and resolves dependencies: an external per-process file in the build directory ≠ a bundled repo profile shipped in the artifact ≠ a local dev config; runtime scope ≠ test scope. Confirm the mechanism by *reading the deployment* (the process manager, the directory layout, the dependency tree with scopes) — do not infer it from a gitignore, from local behavior, or from green tests. If an override lives outside version control, the provenance says so in plain text (the repo does not enforce it; deployment owns applying it). An unconfirmed mechanism → STOP, do not guess. (Illustration: a bundled config file left untracked → make the annotation default fail-safe, or a clean checkout boots on the framework's default port.)

**A live process start is a DoD item** for any change touching classpath, dependencies, datasource, port, launch config, or a cross-process contract (including the front↔back address). The DoD requires an *actual* process start and a request passing through — not only `build`/`test`. Test scope suffices for tests, not for runtime; a green CI does not prove the process starts and listens on the right port. Verify on the live process (a startup log + a real request/response code); where scriptable, a smoke-boot in CI or the Stop-hook. (The start is initiated by the process owner — see the instructions file.)

---

## Questions gate the prompt (hard for HEAVY/config/deploy; LIGHT defaults in one line)

This governs the prompt *author* (before the prompt is written), not the agent's behavior. Before writing the prompt, resolve unknowns by asking — do not guess and do not patch as you go.

**Hard for HEAVY / config / deploy / integration.** Where an unknown fact has a wrong-default = rework cost → STOP and ask, wait for the answer, then write. **Never reconstruct config keys / ports / paths from memory** — a gating value is an evidence class (it comes from the code/deployment, not from your head; see the deployment-requirements rule). Pull *all* gating facts before the prompt, in *one* round.

**Anti-pattern (forbidden) — turn-by-turn revise:** rewriting an already-written prompt for each newly-surfaced fact, in circles. A string of facts each of which flips the decision is the signature of a skipped round of questions — they should have been gathered in one pass before the prompt.

**LIGHT (a one-sentence diff, a safe default)** — skip the questions, name the default in one line; do not multiply questions on the trivial. But a fork where a wrong default = rework is not swallowed silently even in LIGHT — one short question is cheaper than the rework.

---

## The portable layer is project-agnostic (methodology + playbooks)

`docs/process/` and `docs/playbooks/` are the *portable* layer (they travel to new projects). **Rule bodies here are project-agnostic:** no person names, and no project literals (a repo / server / stand / external-system name, a ticket key) *as the norm*.

**A person's name → a role,** by grammar, not mechanically: a third-person description → "the engineer" ("the engineer holds the commit-gate"); a direct instruction → "you" ("if you fixed it twice — …").

**Project specifics are allowed ONLY as:**
- a *marked* illustration / precedent / incident — "(e.g. …)", "(Precedent: …)", "**Incident:** …" — read as an EXAMPLE, not a norm;
- a *reference exemplar* pointing at a concrete class / entity / file — a model implementation (where to look), not a universal rule; the class name is the value of the reference and is NOT anonymized;
- a *structural pointer* (a path / a subsystem's canonical doc / a sibling repo's paired playbook) or an index's repo self-identification ("Playbooks (&lt;repo&gt;)") — an address, not a norm.

**A ticket key (`ABC-123`) is not in the rule body:** tracing lives in commit messages and the provenance log, not in a portable rule.

**Canonical subsystem docs are exempt:** there, project specifics (entities, endpoints, stands) are the *subject* of the doc, not a violation — this rule is for the portable layer only. Future `.md` in this layer follows agnosticism strictly (a review-gate: a person's name or a project-literal-as-norm in a portable rule body = a blocker).

---

## KISS — the simplest path under real uncertainty (recon is not the default)

The recon / HEAVY cycle is a tool for *real* uncertainty, not a default. If a fix is one sentence AND the root is already narrowed to a candidate (a prior recon or plain obviousness named the likely source) → go straight to BUILD with a GATE-0 check of that candidate (+ STOP if it is not the one), with no separate recon round. Do not order a second recon when the previous one already narrowed the problem to a candidate — hit it with a BUILD. KISS does not waive evidence: simplicity means less ceremony, NOT less proof — the candidate check lives in GATE-0 inside the BUILD, it is not dropped.

---

## Anti-overhead — a locked plan → command directly, not a duplicate prompt

When a plan is already locked (a plan doc) and a pass proceeds *from* it with no new delta (scope + provenance already in the plan, no forks) — the author does NOT write a BUILD prompt; the operator commands the agent directly ("do &lt;step&gt; per the plan"). A prompt that merely duplicates the plan is pure overhead. A prompt is needed ONLY on a delta to the plan: a fresh fact after recon, a fork for the operator to decide, or a pass outside the plan (LIGHT / FIX / RULE / DOCS). Nothing to add beyond the plan → do not write one. (The "do not proliferate" principle applied to the prompts themselves.)

---

## Branch base = the dependency's location, not blindly the main line

**A branch is opened per task, with its own id;** the sub-steps and addenda of one task are commits to *its* branch, not a new one.

**Do not auto-base a new branch off the main line by default.** The base of a new branch = the *location of its dependency*: if a code-dependency is not in the main line yet (it lives in a feature branch), stack the new branch *off that branch*, not off main — otherwise the file to edit is not in the base and there is nothing to change.

**Merge order = the dependency first,** then rebase the branches stacked above it. The author does not pick the base silently — proposes it and checks with the operator. (This is branch *topology*; it makes no assumption about what merging the main line triggers — a project states its own release/deploy cadence separately.)

---

## Editing shared / base code = HEAVY by definition; GATE-0 asks WHY

Changing common/base code that already has consumers (a shared library, base ancestor classes, common services several contours inherit from) is HEAVY *by definition*, even when the trigger is cosmetic or local to one contour. The blast radius is every consumer, not just yours. Therefore:

**(1)** Such a change does NOT go on the LIGHT track: even a one-line replacement in a common ancestor is reviewed as architectural — the rollback boundary is wider than the touched file.

**(2)** Before replacing a shared API, GATE-0 must answer "WHY is the current implementation THIS way" — not only "who calls it." The choice may have been deliberate (a specific capability another consumer depends on). Recon "can this be removed" ≠ recon "why it was chosen": the first finds callers, the second finds the reason.

**(3)** "Isolate a feature in contour X" means do NOT touch the common ancestor: a contour-local service/wrapper called by X's components, not a swap of the implementation in the shared base. If the "isolation" edit lands in the shared ancestor, the isolation boundary is broken — that is no longer isolation. (Precedent: swapping a deliberately-chosen provider in a common base component — chosen for a capability a *different* consumer relied on in several places — broke that consumer; revert. The right move was a contour-local service, leaving the base untouched.) This complements the read-only-core rule: that one forbids editing a parent core; this one is about the shared layer *with* consumers (finer — editing is allowed, but the blast radius makes it HEAVY).

---

## Default acceptance is unit + integration-slice; live-UI verification is opt-in

The default Definition-of-Done acceptance for a UI change is **unit + slice-level** tests (component/unit tests on the front, a web-layer slice test on the back) — not a live browser/E2E run. A live-UI pass (browser automation, a real render, computed styles) is **opt-in, by the operator's explicit request,** not an automatic DoD line on every pass. This draws the line cleanly: visual regression, layout, and animation need a live eye (a slice test can't catch them) — logic, state, and contract are fully covered by unit/slice. Don't default an agent onto a live-UI tool that may be disabled or out of budget for a given project/profile; state the project's own default explicitly in its own instructions file if it differs (e.g. a project keeping both a unit-level and a live-UI test tool available may leave the live one off by default, turning it on only when the operator asks).

---

## Direct-access boundary is by work class, not file extension

Where a direct-filesystem tool is available to the assistant (outside the coding-agent's own prompt flow), the boundary on what it may edit directly is **by class of work, not by file extension:** docs / tooling scripts / build-and-lint config (a portable-layer doc, a maintenance script, a package-manager or bundler config) may be edited directly when such a tool exists — but **product/feature code** (application logic, entities, controllers, components — anything that ships the product) always goes through the coding-agent's own BUILD/FIX prompt flow, never a direct edit, regardless of whether the direct-access tool is technically capable of it. A tool's capability is not authorization: the ability to write a file is not permission to bypass the implementation flow for product code.

---

## Meta-principle

The best prompt = **seasoned project context + precise boundaries + known traps + clear STOP-points.** Not a "clever" prompt, a PRECISE one: the agent knows what to look for, where to stop, what not to touch, which traps await. Prompt quality grows from accumulated project knowledge (recons, patterns, traps), not from wording.
