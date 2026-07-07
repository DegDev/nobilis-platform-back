#!/usr/bin/env bash
# methodology-backport.sh — bracket the recon-first-canon backport with two mechanical steps.
#
# The FOLD itself (translate to English, strip external-project names to marked
# illustration, triage fold/drop/adapt against this project's distribution model) is a
# JUDGMENT pass and is deliberately NOT scripted — a cp/sed would reproduce exactly the
# clobber and the clean-room breach that recon exists to catch. This script only brackets
# that pass:
#   survey  — BEFORE the fold: heading-level diff of source vs target canon (read-only).
#   gate    — AFTER the fold:  mechanical clean-room + English-strict + pointer checks.
# Neither subcommand commits. `gate` prints ready commit blocks on green.
#
# Clean-room: NO external/private project names are hardcoded here (this file is public).
# The name grep reads an optional blocklist (one extended-regex or word per line, '#'
# comments and blanks ignored) from $CLEANROOM_BLOCKLIST, else scripts/cleanroom-blocklist
# (gitignored). Absent -> the name grep is skipped with a note; the other checks still gate.
#
# Usage:
#   scripts/methodology-backport.sh survey <source-repo-root> <target-back-repo-root>
#   scripts/methodology-backport.sh gate   <target-back-repo-root> <target-front-repo-root>

set -uo pipefail

METHO_REL="docs/process/prompting-methodology.md"
SLOG_REL="docs/sources-log.md"
POINTER_MAX_LINES=30   # a front pointer must stay small
CANON_MIN_LINES=100    # the back canon must stay substantial
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "ERROR: $*" >&2; exit 2; }

resolve_blocklist() {
  if [ -n "${CLEANROOM_BLOCKLIST:-}" ]; then
    [ -f "$CLEANROOM_BLOCKLIST" ] && { echo "$CLEANROOM_BLOCKLIST"; return 0; }
    return 1
  fi
  local f="$SCRIPT_DIR/cleanroom-blocklist"
  [ -f "$f" ] && { echo "$f"; return 0; }
  return 1
}

blocklist_regex() { grep -vE '^[[:space:]]*(#|$)' "$1" | paste -sd'|' -; }

cmd_survey() {
  local src_repo="${1:-}" tgt_repo="${2:-}"
  { [ -n "$src_repo" ] && [ -n "$tgt_repo" ]; } || die "usage: $0 survey <source-repo-root> <target-back-repo-root>"
  local src="$src_repo/$METHO_REL" tgt="$tgt_repo/$METHO_REL"
  [ -f "$src" ] || die "no source methodology at $src"
  [ -f "$tgt" ] || die "no target canon at $tgt"

  echo "== SURVEY (read-only, heading-level) =="
  echo "source: $src"
  echo "target: $tgt"
  echo
  echo "--- '## ' headings only in SOURCE (candidate-new to fold) ---"
  comm -23 <(grep -E '^## ' "$src" | sort) <(grep -E '^## ' "$tgt" | sort) || true
  echo
  echo "--- '## ' headings only in TARGET (already present / target-specific) ---"
  comm -13 <(grep -E '^## ' "$src" | sort) <(grep -E '^## ' "$tgt" | sort) || true
  echo
  echo "NOTE: headings only. The actual fold is a judgment pass — translate to English,"
  echo "      strip external names to marked illustration, triage fold/drop/adapt against"
  echo "      the distribution model. Do NOT cp/sed the source body."
}

cmd_gate() {
  local back="${1:-}" front="${2:-}"
  { [ -n "$back" ] && [ -n "$front" ]; } || die "usage: $0 gate <target-back-repo-root> <target-front-repo-root>"
  local canon="$back/$METHO_REL"
  local files=( "$back/$METHO_REL" "$back/$SLOG_REL" "$front/$METHO_REL" "$front/$SLOG_REL" )
  local fail=0

  echo "== GATE =="

  echo "-- English-strict (no Cyrillic) --"
  local f n
  for f in "${files[@]}"; do
    if [ ! -f "$f" ]; then echo "  MISSING   $f"; fail=1; continue; fi
    n=$(grep -cP '\p{Cyrillic}' "$f" 2>/dev/null || true)
    if [ "${n:-0}" -gt 0 ]; then echo "  CYRILLIC($n)  $f"; fail=1; else echo "  ok         $f"; fi
  done

  echo "-- clean-room name grep --"
  local bl rx m
  if bl=$(resolve_blocklist); then
    rx=$(blocklist_regex "$bl")
    if [ -z "$rx" ]; then
      echo "  (blocklist empty — skipped)"
    else
      for f in "${files[@]}"; do
        [ -f "$f" ] || continue
        m=$(grep -niE "\\b(${rx})\\b" "$f" || true)
        if [ -n "$m" ]; then echo "  HIT  $f"; echo "$m" | sed 's/^/       /'; fail=1; else echo "  ok   $f"; fi
      done
    fi
  else
    echo "  (no blocklist — set \$CLEANROOM_BLOCKLIST or create $SCRIPT_DIR/cleanroom-blocklist; skipped)"
  fi

  echo "-- front pointer sanity --"
  local fp="$front/$METHO_REL" fl cl
  if [ -f "$fp" ]; then
    fl=$(wc -l < "$fp")
    if [ "$fl" -le "$POINTER_MAX_LINES" ]; then echo "  ok    front methodology is $fl lines (<= $POINTER_MAX_LINES)"; else echo "  BIG   front methodology is $fl lines (> $POINTER_MAX_LINES) — not a pointer?"; fail=1; fi
    if grep -q "prompting-methodology.md" "$fp"; then echo "  ok    pointer references the canon path"; else echo "  MISS  pointer does not reference the canon path"; fail=1; fi
  else
    echo "  MISSING   $fp"; fail=1
  fi
  if [ -f "$canon" ]; then
    cl=$(wc -l < "$canon")
    if [ "$cl" -ge "$CANON_MIN_LINES" ]; then echo "  ok    back canon is $cl lines (>= $CANON_MIN_LINES)"; else echo "  THIN  back canon is $cl lines (< $CANON_MIN_LINES)"; fail=1; fi
  else
    echo "  MISSING   $canon"; fail=1
  fi

  echo
  if [ "$fail" -ne 0 ]; then echo "GATE: RED — fix the above before committing."; return 1; fi
  echo "GATE: GREEN"
  echo
  print_commit_blocks "$back" "$front"
}

print_commit_blocks() {
  local back="$1" front="$2"
  cat <<EOF
Ready commit blocks (run them yourself — the commit-gate is the operator's; never let the agent push):

  cd "$back"
  git add $METHO_REL $SLOG_REL
  git commit -m "docs(process): backport proven prompting rules into the recon-first canon" \\
             -m "EN + project-agnostic (clean-room). Provenance in sources-log."

  cd "$front"
  git add $METHO_REL $SLOG_REL
  git commit -m "docs(process): sync front methodology pointer (SSOT in back)" \\
             -m "Provenance in sources-log."
EOF
}

case "${1:-}" in
  survey) shift; cmd_survey "$@" ;;
  gate)   shift; cmd_gate "$@" ;;
  *) echo "usage: $0 {survey <source-repo> <target-back> | gate <target-back> <target-front>}" >&2; exit 2 ;;
esac
