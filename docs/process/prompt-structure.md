# Prompt Structure — nobilis-platform-back

_Type: process doc (prompt format) · Scope: back · Source of truth: prompt FORMAT lives here, agent
BEHAVIOUR lives in [CLAUDE.md](../../CLAUDE.md) · Updated: 2026-06-30_

**Format here, behaviour there.** This doc says how to *shape* an agent prompt. It never restates
what the agent already does by standing rule — those live in [CLAUDE.md](../../CLAUDE.md). A prompt
may name a rule in one reminder line, but must not rewrite it. **On any conflict, CLAUDE.md wins.**
Agent-neutral: applies to Claude Code or any agent.

## When to apply

Before writing **any** agent prompt — recon, build, docs, or fix. Pick the prompt type first
(below), then fill its skeleton. For weight (do I even need recon?) see
[two-track-workflow.md](./two-track-workflow.md).

## Agent behaviour is NOT in the prompt

These are standing rules in [CLAUDE.md](../../CLAUDE.md); a prompt references them in at most one
reminder line and never re-specifies them:

- **Working principles** — think-before-code / STOP-on-ambiguity, minimal code, surgical edits,
  goal-driven verification → CLAUDE.md _Working principles_.
- **Commit gate** — finish = STOP + report + proposed commit message; the agent does not self-commit
  → CLAUDE.md _Commit gate_.
- **Protected boundary** — read-only dependency / engine core is not edited or forked
  → CLAUDE.md _Boundary: engine vs domain_.
- **MCP usage** — navigate/inspect via the IntelliJ index (Java symbols, Maven modules), not text
  search or memory → CLAUDE.md _MCP servers — mandatory usage_ (`jetbrains`).
- **Package-by-feature** — organize by capability, never by artifact-type buckets
  → CLAUDE.md _Package structure — package-by-feature_ and [conventions.md](../conventions.md).
- **Conventions** — formatting/lint are gated in CI (Spotless + google-java-format + Checkstyle)
  → CLAUDE.md _Code conventions_.

If a draft prompt starts re-explaining one of these, cut it to a single pointer line.

## Structure by type

### RECON (read-only)

- **Header:** `MODE: read-only`.
- **Body:** numbered questions.
- **Answers contract:** every answer as `file:line` + a verbatim quote — **not a paraphrase**.
- **STOP block:** list exactly what to return, then end with **"do not touch code"**.
- **No** DoD, no implementation parts — recon produces findings, not changes.

### BUILD

- **Header:** the task in one line · mode · an MCP reminder line · a commit-gate reminder line ·
  package-by-feature · what **NOT** to touch (the protected boundary).
- **GATE-0:** recon-inside — confirm the real spots by `file:line` before editing; an explicit
  **"STOP if `<X>` is impossible → return a verdict, don't invent a workaround."**
- **Recon-confirmed context:** mark facts already verified **"do not re-verify"** so the agent
  doesn't burn the budget re-deriving them.
- **PARTS A / B / C:** each self-contained (a part reads without needing the others open).
- **DoD (numbered):** green build (`mvn -B verify`) · tests pass · no regression · Flyway migration
  if schema touched · package-by-feature respected · Spotless/Checkstyle clean · protected boundary
  untouched.
- **Close:** **"STOP + report + proposed commit message"**, then an explicit out-of-scope list.

### DOCS

- **View the target first, then patch** the specific spot — **not** a full rewrite (a rewrite loses
  existing content). Keep the change surgical.
- Sync any mirrors / indexes the doc is registered in.
- Include the commit-gate reminder line.

### FIX

- Find the **root cause by fact, not guess** (usually a recon-inside step with `file:line`).
- Keep the **correctness fix and any refactor in separate commits**.
- Don't touch control flow for "cleanliness" — surgical only.
- Include the commit-gate reminder line.

## Pre-send checklist

1. Prompt type chosen (recon / build / docs / fix).
2. MCP reminder line in the header.
3. Commit-gate reminder line present (build / docs / fix).
4. Recon-confirmed facts marked **"do not re-verify"**.
5. DoD numbered, incl. tests + regression (+ Flyway migration if schema touched).
6. STOP-conditions named (what makes the agent stop and report instead of improvising).
7. Out-of-scope drawn (build prompts).

## When NOT to force the format

- A trivial 1–2 line edit — don't scaffold a full BUILD prompt around it.
- Plain Q&A — no DoD, no parts.
- A recon prompt carries no DoD and no implementation parts by definition.

## Contract (quick read)

A prompt follows the **FORMAT** defined here; the agent's **BEHAVIOUR** comes from
[CLAUDE.md](../../CLAUDE.md) and is not overridden by prompt wording. On conflict, CLAUDE.md wins.
Agent-neutral.
