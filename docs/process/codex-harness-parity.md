# Codex CLI harness parity

> **Snapshot — as of OpenAI Codex CLI ~v0.124–v0.144 (July 2026).** Codex moves fast; every
> version-specific claim below (config keys, hook shapes, issue numbers) must be **re-verified**
> against Codex's live docs before you rely on it. This is the distilled map — the full dated
> research lives outside the repo (project context), not reproduced here.

This project's harness was built for Claude Code (CC). nobilis is also an AI-augmented-dev
showcase, and running the *same* harness on a second agent (Codex) for A/B is a real, in-use
workflow — Codex A/B recons already happen. This doc records how each CC harness point maps onto
Codex so the parity work isn't re-derived each time.

The territory is **not** built yet: no `.codex/` config, `AGENTS.md`, or symlink exists in the repo.
This is the map; build the pieces on demand (extract-don't-predict), when Codex use becomes routine.

## The three transfer categories

Every harness point falls into one of three buckets by how it transfers to Codex.

### 1. Single-source — zero duplication

These are shared as-is; one canonical copy serves both agents.

- **`CLAUDE.md`** → point Codex at it, don't duplicate. Two options: a symlink
  `AGENTS.md → CLAUDE.md` (git stores symlinks natively; works on clone for Unix — note Windows
  needs `core.symlinks=true`, a rough edge for a public repo), OR
  `project_doc_fallback_filenames = ["CLAUDE.md"]` in `~/.codex/config.toml` (Codex reads CLAUDE.md
  with no repo file at all — cleaner, no Windows issue, keeps CLAUDE.md the single SSOT). The
  fallback is only consulted when `AGENTS.md` is absent at that directory level.
- **Referenced docs** (`docs/conventions.md`, `docs/sources-log.md`, `docs/process/*`,
  `docs/playbooks/*`, `.agent/plans/*`) — ordinary repo files both agents read. No duplication.
- **Skills** — the `SKILL.md` body is portable across CC/Codex (open Agent Skills standard). Only
  location + metadata differ (CC scans `.claude/skills/`, Codex `.agents/skills/`); symlink or a
  shared tree avoids copies.
- **MCP servers** (context7, jetbrains, playwright) — single definition, already in
  `~/.codex/config.toml` with the same servers as the CC session. Full tool-calling parity.
- **Git-level hooks** (`.githooks/` pre-commit, pre-push) — operate below any agent, gate every
  actor identically, **zero Codex adaptation**. This is the real enforcement layer.

### 2. Thin Codex-specific shim — small, but must exist

- **`.codex/config.toml`** — sets `project_doc_fallback_filenames`, `writable_roots` for the sibling
  repo, MCP servers if not global.
- **format-hook wrapper** — Codex passes hook context as JSON on **stdin** and sets **no
  `CLAUDE_PROJECT_DIR`-style env var**. The existing `format-file.sh` logic (google-java-format,
  prettier) is reusable, but needs a small head that parses the changed path out of the stdin JSON
  instead of an env var. Only the input-parsing differs.

### 3. Maintained in parallel — DRIFT RISK (flag each)

No mechanism references the CC copy; these are kept in sync by hand.

- **`.codex/hooks`** (PostToolUse format, Stop verify) — no reference to `.claude/settings.json`
  hooks. If you change format/verify behaviour in one, you must mirror it.
- **`.codex/agents/*.toml`** (recon, reviewer) — TOML vs CC's MD-frontmatter, no cross-reference.
  Keep the TOML `developer_instructions` short and have it point at a shared repo markdown role file
  to minimize drift.
- **`/plan`** — CC's slash command has no Codex equivalent; rebuild it as a **Skill** (explicitly
  invokable, e.g. `$plan`, `allow_implicit_invocation: false`). Back it with a `scripts/` scaffolder
  for determinism.

## Per-harness-point mapping

| CC harness point | Codex equivalent | Verdict |
|---|---|---|
| `CLAUDE.md` always-load | `AGENTS.md` symlink OR `project_doc_fallback_filenames` | single-source |
| `settings.json` permissions | `approval_mode` / `trust_level` in `config.toml` | thin shim |
| PostToolUse format hook | `.codex/hooks` PostToolUse + stdin-JSON wrapper | parallel-maintain |
| Stop verify hook | `.codex/hooks` Stop — **soft only** (see limits) | parallel-maintain |
| `/plan` command | rebuild as a Skill (`$plan`) | parallel-maintain |
| recon + reviewer subagents | `.codex/agents/*.toml` | parallel-maintain |
| skills (`SKILL.md`) | `.agents/skills/` — body portable | single-source |
| MCP (context7/jetbrains/playwright) | same servers in `config.toml` | single-source |
| `.githooks/` pre-commit/pre-push | same hooks, agent-agnostic | single-source (as-is) |
| `local-config.json` sibling-repo | `--add-dir` + `writable_roots` (see limits) | no clean equivalent |

## Two hard limitations to design around

1. **No clean single-session sibling-repo equivalent to `local-config.json`.** Codex has no
   first-class way to hold two sibling repos as one session's roots. Workaround: launch from one
   repo and use `--add-dir ../other-repo`, grant writes via
   `sandbox_workspace_write.writable_roots` in `config.toml`. Not as clean as `local-config.json`;
   no single Codex commit can span both repos — orchestrate git from the shell.

2. **The Stop hook can't hard-block completion** the way `verify-on-stop.sh` does. A Codex Stop hook
   returning a block only injects a *soft continuation prompt* — the model can still stop. So
   `mvn -B verify` gating via the Stop hook is best-effort, not enforced. **Real enforcement must
   stay at the git-hook + CI layer** — which already matches this project's "physical barrier below
   the agent" principle (`.githooks` + CI gate the human too).

Also: standardize on the **CLI-in-terminal**, not the VS Code extension — the extension has
documented gaps (hooks may not fire, profile/MCP fidelity), disqualifying for harness parity.

## What NOT to build yet

No `.codex/` hooks, agents, or config are created in this repo now — this is extract-don't-predict:
build them when Codex use becomes routine, not ahead of it. Today Codex already runs with the shared
MCP servers and can read `CLAUDE.md`; that's enough for A/B recons. The first pieces worth building,
in order: (1) the `CLAUDE.md` single-source link, (2) the format-hook stdin wrapper, (3) `/plan` as
a Skill. Everything else stays on the map until a real need pulls it into the territory.
