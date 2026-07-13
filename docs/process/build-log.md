# Build log — verified outcomes per slice

A factual record, one row per BUILD/FIX/RECON/DOCS slice, appended and committed atomically with
the slice's code (one commit = code + its outcome row). The row reflects the **verified** outcome
— established by independent verification and/or a live check — not the executing agent's
self-report. See the ritual in [prompting-methodology.md](./prompting-methodology.md).

## Columns

| column   | meaning |
|----------|---------|
| milestone | the milestone id the slice belongs to |
| slice    | the slice's short name |
| type     | RECON / BUILD / FIX / DOCS |
| planned  | Y — was in the milestone's slice plan; N — surfaced (e.g. a fix pulled in mid-milestone) |
| outcome  | clean / fix-from-live / fix-from-verify / rework / gate-stop / superseded |
| source   | what established the outcome: verify / browser / curl / ollama / hikari-log / — |
| notes    | one line: what was checked, what broke (if anything), how it was caught |

**Outcome vocabulary:**
- `clean` — passed verification/live check on the first attempt, no fix needed.
- `fix-from-live` — the build/test was green, but a live check (browser, curl, a real service) caught a defect the tests missed; fixed within the same slice.
- `fix-from-verify` — independent verification (not the executing agent's own report) caught a defect; fixed within the same slice.
- `rework` — the executing agent's approach itself was wrong; the prompt had to be rewritten and the slice redone. The true miss — not caught by a gate, caught by the operator.
- `gate-stop` — a GATE-0 check stopped the slice before any code was written (a false premise, an impossible ask).
- `superseded` — the slice's output (usually a RECON) was replaced by a later, more complete pass before it was acted on.

## What this log supports

- **Planning determinism** — a milestone planned as N slices should ship N slices. Fixes caught
  within a slice are rows of their own, not extra slices; a planned:N slice that stays one row is
  determinism holding.
- **First-pass yield** — `(clean + fix-from-live + fix-from-verify) / total`. `clean-pass` =
  `clean / total`. `rework` = `rework / total`. Fixes caught by the method's own gates (a live
  check, an independent verify) are **successes** — the defect was caught before merge, exactly
  what the gate is for. `rework` is the true miss: the executing agent's approach had to be thrown
  out and re-prompted. Distinguishing the two is the point of this log — a raw "how many slices
  needed a fix" number conflates a gate doing its job with a genuine miss.

## Log

| milestone | slice | type | planned | outcome | source | notes |
|---|---|---|---|---|---|---|
| 05 | pass1 back      | BUILD | Y | fix-from-verify | verify/DC  | Codex reported green; ISO-8859-1 charset defect caught by independent verify → engine FIX |
| 05 | pass2 common    | BUILD | Y | clean           | verify     | 39 tests green first pass |
| 05 | pass3 bootstrap | BUILD | Y | clean           | verify     | AOT + tests green first pass |
| 05 | pass4 strings   | BUILD | Y | fix-from-live   | browser    | build green; empty-stub ({id:""}) blanked UI, caught in browser → FIX (seeded stubs) |
| 05 | pass4 spec-fix  | FIX   | N | fix-from-verify | verify     | overlay spec registered 0 tests (node:fs in browser-mode Vitest), caught by verify → FIX (JSON import) |
| 06 | recon#1         | RECON | Y | superseded      | —          | initial thin env-config assumption superseded by a full metadata-driven scope decision |
| 06 | recon#2         | RECON | Y | gate-stop       | context7   | surfaced native /api/chat (Fork 5) vs OpenAI-compat; premise corrected before any code |
| 06 | s1 schema       | BUILD | Y | clean           | verify     | 7 tables, ddl-validate green first pass |
| 06 | s2 services     | BUILD | Y | clean           | verify     | 22 tests green first pass, EMF+Crypto gate correct |
| 06 | s3 client       | BUILD | Y | fix-from-live   | curl       | build green; qwen3 &lt;think&gt; leak, caught in live curl → FIX (native think:false) |
| 06 | s4 controller   | BUILD | Y | clean           | curl       | build green; live health-check ok=true against real Ollama |
| 06 | s5 front form   | BUILD | Y | fix-from-live   | browser    | build green; decimals rejected (missing step), caught in browser → FIX (step="any") |
| 06 | s6 async        | BUILD | Y | clean           | verify     | 10 tests green first pass, bus terminal/retriable correct |
| polish | pool-config  | FIX   | N | clean           | hikari-log | infra defaults moved base←local; verified maximumPoolSize=10 live |
| polish | logging L1   | BUILD | N | clean           | verify     | logback per-runnable files + configurable levels, green first pass |
| polish | meta-annotation p1 | BUILD | Y | clean | curl | @NobilisAdminController folds @RestController+@RequiresPermission (via @AliasFor); @RequestMapping stays per-controller with ${nobilis.api.v1.url:/api} (AliasFor can't concat base+segment); proven on AccountController, path /api/admin/accounts live 200, old 404; 18 admin tests green |
