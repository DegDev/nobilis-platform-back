# Two-Track Workflow — nobilis-platform-back

_Type: process doc · Purpose: choose the depth of work by the weight of the task · Updated: 2026-06-30_

**Match effort to the task.** Not every request needs recon; not every request can skip it. Before
starting work, pick a track by the weight of the task and name the choice in one line.

## When

On **every** task from the user — pick the track *before* starting work, not after sliding into it.

## The two tracks

- **LIGHT** — cosmetics, renames, small UI tweaks, an obvious entry point. Go straight to a build
  prompt with a short inline gate; no separate recon pass. Make reasonable default assumptions and
  flag each in one line. Ask one short question only when a fork genuinely changes the result.

- **HEAVY** — architecture, a change at a protected boundary (a read-only dependency, the engine
  core), a new pipeline, or unknown integration points. Do read-only **recon before design**. Recon
  is justified when not knowing the spot risks rework.

## Track-selection checklist

1. **Entry point obvious?** Yes → light. No → heavy.
2. **Touches architecture / a protected boundary / a new mechanism?** Yes → heavy.
3. **Could a wrong default cause rework?** Yes → a short question, or heavy.
4. **An agent task at all?** If it's a one-off op (a data edit, a config change), say so plainly —
   don't wrap it in a prompt.

## Rules (both tracks)

- The track is the user's choice if they state it; otherwise the agent picks by weight and **names
  it in one line**.
- Clarifying questions only on genuine ambiguity — where a wrong default means rework — not on every
  minor decision.
- Dense answers: a decision plus a brief why, not an essay.
- Recon is not ceremony — run it only when it pays off.
- Trivial things may not be agent tasks at all.

## Anti-patterns

- Don't run heavy-track recon on cosmetics.
- Don't dump questions on minor things — pick a default and state the one-line assumption.
- Don't wrap a one-off op in an agent prompt — say "do it this way, no agent needed".

## Contract (quick read)

Task weight → track. **Light** = straight to build + sensible defaults. **Heavy** = recon first.
The user can override the track explicitly. Ask a question only on ambiguity. Keep answers dense.
