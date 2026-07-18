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

---

## REFERENCE TEMPLATES (copy the format; replace <...>; prompt in English; each plate = one code block, don't break it)

### RECON

```
RECON — read-only. DO NOT modify code/config/migrations, no commits. Repo: <repo> (<path>), branch <branch>. <source-of-truth note if any>. No branch switch. Use jetbrains MCP for navigation/usages; context7 before asserting any library/framework API.

## Context
<what's known, what triggered this, the locked premise/decision>. <key artifacts/files under investigation>.

## Tasks (decisive-first)
1. DECISIVE: <cheapest fact that confirms/kills the leading hypothesis>.
2. <trace/enumerate/classify — file:line + quote required>.
3. <data-safety/blast-radius/dependency check>.

## STOP
Return:
A. <bucket A>.
B. <bucket B>.
C. <...>.
F. Premise-overturns/surprises.

Read-only. No code, no DoD. Subagent only if broad → Sonnet.
```

### BUILD

```
BUILD — <one-line goal>. <LIGHT|HEAVY>. Repo: <repo> only. Branch: <branch> (base <base>; switch yourself, checkout≠commit). Do NOT commit. Use jetbrains MCP explicitly; context7 before any library/framework API; playwright for any front UI verification (milestone 03+). Engine = mechanism, domain concerns stay out.

## GATE-0 (before code)
1. Confirm file:line entry point (quote, not paraphrase).
2. STOP if <X> impossible → return verdict, don't invent a workaround.

## Locked decisions (numbered — CC does not re-decide)
<decision agreed, one line each>.

## Tasks by commit
Commit 1: <minimal self-contained change>.
Trap inline: <known pitfall here>.

## DoD
Build green — `mvn -B verify` (back) / `node_modules/.bin/ng build <project> --configuration=development` + Vitest (front, AOT = the only complete check); tests on touched paths, no regression; package-by-feature respected; Flyway migration if schema touched (back); Spotless/Checkstyle clean (back); i18n same pass for any new user-facing string (front); UI changes verified in the running browser via playwright (front, milestone 03+); no secrets; branch correct; sources-log updated for any non-trivial decision; protected boundary untouched.

## STOP
Report (what done, how verified) + proposed commit text `type(scope): subject`.
Out of scope: <what we don't touch this pass>.
```

### FIX

```
FIX — <bug one-liner>. <LIGHT|HEAVY>. Repo: <repo> only. Branch: <branch>. Do NOT commit. Use jetbrains MCP; context7 if a library API is in question.

## Root (fact, not guess — recon-confirmed/code-quoted)
<file:line + quote of the actual defect>.

## GATE-0
1. Confirm the root above; STOP + report if different (don't patch blind).

## Fix
<surgical change; don't touch control-flow for "cleaner">; separate commits: correctness vs refactor (don't mix).

## DoD
Build green — `mvn -B verify` (back) / `node_modules/.bin/ng build <project> --configuration=development` (front); a test reproducing the bug (was-red-now-green); i18n same pass if user-facing (front); no secrets; sources-log if the cause is non-obvious.

## STOP
Report + proposed commit text `fix(scope): subject`.
```

### DOCS

```
DOCS — <what doc/playbook, why>. Edit ONLY the .md. No code, no BUILD, no commits.

## Changes
View first → surgical patch, NOT full rewrite (lesson: rewrite loses content); sync mirrors/indexes (README, paired back/front docs, internal links); <what to add/change, section by section>.

## STOP
Report which file(s) edited + the added section + proposed commit text `docs(scope): subject`.
```

### RULE

```
RULE — <behavioral/process rule to encode, where it lives>. Edit ONLY the target md. No commits.

## Rule
<the rule, imperative, one place of truth>; behavior → CLAUDE.md, format → prompt-structure.md, process → prompting-methodology.md (don't duplicate).

## GATE-0
1. grep ALL occurrences; update every one (no drift between copies).

## STOP
Report edited files + proposed commit text `docs(methodology): subject`.
```
