#!/usr/bin/env bash
# Stop hook: run a full verify of every tree this session may have touched, and
# BLOCK ONCE if red (anti-spin — surface the failure and stop, never loop). Each
# tree is skipped when its working tree is clean (porcelain-scoped), so a back-only
# sub-task doesn't pay front build time and vice-versa. Byte-identical across repos.
#
# The verify COMMANDS themselves live once, in scripts/verify.sh (canon: back repo)
# — this hook is a thin wrapper that adds only the hook-specific concerns: the
# stop_hook_active gate, porcelain/docs-only scoping, and exit-2 blocking.
set -uo pipefail

# Already continuing from a previous Stop block? Let it stop (block-once).
[ "$(cat | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

root="$CLAUDE_PROJECT_DIR"
sibling="$(jq -r '.frontend_path // .backend_path // empty' "$root/.claude/local-config.json" 2>/dev/null)"

# Canon scripts/verify.sh lives in the back repo only — resolve it whether this
# hook is running from back (root) or front (sibling).
if [ -f "$root/scripts/verify.sh" ]; then
  verify_sh="$root/scripts/verify.sh"
elif [ -n "$sibling" ] && [ -f "$sibling/scripts/verify.sh" ]; then
  verify_sh="$sibling/scripts/verify.sh"
else
  verify_sh=""
fi

# A dirty tree with EVERY changed path matching a docs-only glob (*.md, .agent/**,
# docs/**) has no buildable delta -- skip its build the same way a clean tree is
# skipped. CI runs a clean build on every push regardless; this is a fast local
# signal, and building on a doc-only diff can only ever be a false-red. Matches the
# FULL path (git-status-relative, i.e. repo-root-relative) so e.g. "x.md.ts" does
# NOT match "*.md" (suffix match on the whole path, not a substring search).
is_docs_only() {
  local repo="$1" line path
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    path="${line:3}"
    case "$path" in *' -> '*) path="${path##*-> }" ;; esac
    path="${path%\"}"; path="${path#\"}"
    case "$path" in
      *.md|.agent/*|docs/*) ;;
      *) return 1 ;;
    esac
  done < <(git -C "$repo" status --porcelain 2>/dev/null)
  return 0
}

fail=0; msg=""
check() {
  local repo="$1" label log
  [ -d "$repo" ] || return 0
  [ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ] || return 0   # clean tree -> skip
  is_docs_only "$repo" && return 0   # docs-only diff -> nothing buildable changed, skip
  if [ -z "$verify_sh" ]; then
    fail=1; msg="$msg"$'\n'"[VERIFY] scripts/verify.sh not found in $root or $sibling"
    return 0
  fi
  if [ -f "$repo/pom.xml" ]; then label="BACK"; log=/tmp/nobilis-verify-back.log
  elif [ -f "$repo/angular.json" ]; then label="FRONT"; log=/tmp/nobilis-verify-front.log
  else return 0
  fi
  if ! bash "$verify_sh" "$repo" "$log"; then
    fail=1; msg="$msg"$'\n'"[$label] verify RED ($log):"$'\n'"$(tail -n 30 "$log")"
  fi
}

check "$root"
[ -n "$sibling" ] && check "$sibling"

[ "$fail" -eq 0 ] && exit 0
printf '%s\n' "STOP BLOCKED: build is RED (blocking once — fix and finish; a persistently red build surfaces and stops, not loops).$msg" >&2
exit 2
