#!/usr/bin/env bash
# Stop hook: run a full verify of every tree this session may have touched, and
# BLOCK ONCE if red (anti-spin — surface the failure and stop, never loop). Each
# tree is skipped when its working tree is clean (porcelain-scoped), so a back-only
# sub-task doesn't pay front build time and vice-versa. Byte-identical across repos.
set -uo pipefail

# Already continuing from a previous Stop block? Let it stop (block-once).
[ "$(cat | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

root="$CLAUDE_PROJECT_DIR"
sibling="$(jq -r '.frontend_path // .backend_path // empty' "$root/.claude/local-config.json" 2>/dev/null)"

# Resolve a JDK >= 21 for the Java-25 Maven build (box default is 17). Portable, no
# hardcoded path: prefer $JAVA_HOME, then the newest sdkman candidate, then PATH.
find_jdk() {
  local c v
  for c in "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
           $(ls -d "$HOME"/.sdkman/candidates/java/*/bin/java 2>/dev/null | sort -rV) \
           "$(command -v java 2>/dev/null || true)"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    v="$("$c" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    case "$v" in ''|*[!0-9]*) continue ;; esac
    [ "$v" -ge 21 ] && { printf '%s\n' "$c"; return 0; }
  done
  return 1
}

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

# Resolve the Node the FRONT repo wants (.nvmrc-pinned; box default can be older,
# e.g. v22 when v24.18 is required) -- portable, no hardcoded path. Preference
# order: (1) nvm's exact matching install, (2) a PATH node already satisfying the
# version, (3) the newest nvm-installed version that still satisfies it. Never
# silently accepts a too-old node -- returns failure (empty stdout) if none of the
# three satisfy, so the caller can skip instead of false-reding on a version gate.
version_ge() { [ "$1" = "$2" ] || [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ]; }
find_node() {
  local repo="$1" wanted nvmdir c v
  wanted="$(tr -d '[:space:]' < "$repo/.nvmrc" 2>/dev/null)"; wanted="${wanted#v}"
  [ -n "$wanted" ] || return 1
  nvmdir="${NVM_DIR:-$HOME/.nvm}"
  c="$nvmdir/versions/node/v$wanted/bin/node"
  [ -x "$c" ] && { printf '%s\n' "$c"; return 0; }
  c="$(command -v node 2>/dev/null || true)"
  if [ -n "$c" ]; then
    v="$("$c" -v 2>/dev/null | tr -d 'v')"
    case "$v" in ''|*[!0-9.]*) ;; *) version_ge "$v" "$wanted" && { printf '%s\n' "$c"; return 0; } ;; esac
  fi
  for c in $(ls -d "$nvmdir"/versions/node/*/bin/node 2>/dev/null | sort -rV); do
    [ -x "$c" ] || continue
    v="$("$c" -v 2>/dev/null | tr -d 'v')"
    case "$v" in ''|*[!0-9.]*) continue ;; esac
    version_ge "$v" "$wanted" && { printf '%s\n' "$c"; return 0; }
  done
  return 1
}

fail=0; msg=""
check() {
  local repo="$1" jdk jh node_bin node_dir wanted
  [ -d "$repo" ] || return 0
  [ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ] || return 0   # clean tree -> skip
  is_docs_only "$repo" && return 0   # docs-only diff -> nothing buildable changed, skip
  if [ -f "$repo/pom.xml" ]; then
    jdk="$(find_jdk || true)"; jh=""
    [ -n "$jdk" ] && jh="$(dirname "$(dirname "$jdk")")"
    run_mvn() { ( cd "$repo" && { [ -n "$jh" ] && export JAVA_HOME="$jh"; mvn -B "$@"; } ); }
    # Retry-once-on-red: a stale target/ (e.g. an annotation-processor-generated
    # .imports entry surviving a rename/delete under incremental compilation) can
    # fail a build for reasons unrelated to this session's diff. Pay for `clean`
    # only when verify is ALREADY red, not on every green Stop (bounded retry,
    # not a loop -- consistent with the block-once anti-spin above).
    if ! run_mvn verify >/tmp/nobilis-verify-back.log 2>&1; then
      if ! run_mvn clean verify >/tmp/nobilis-verify-back.log 2>&1; then
        fail=1; msg="$msg"$'\n'"[BACK] mvn -B verify RED even after a clean rebuild (stale target/ ruled out) — see /tmp/nobilis-verify-back.log:"$'\n'"$(tail -n 30 /tmp/nobilis-verify-back.log)"
      fi
    fi
  elif [ -f "$repo/angular.json" ]; then
    node_bin="$(find_node "$repo" || true)"
    if [ -z "$node_bin" ]; then
      wanted="$(tr -d '[:space:]' < "$repo/.nvmrc" 2>/dev/null)"
      printf '[FRONT] node %s not found, skipping front verify\n' "${wanted:-?}" >&2
      return 0
    fi
    node_dir="$(dirname "$node_bin")"
    if ! ( cd "$repo" && export PATH="$node_dir:$PATH" \
        && node_modules/.bin/ng build common --configuration=development \
        && node_modules/.bin/ng build admin  --configuration=development \
        && node_modules/.bin/ng build app    --configuration=development \
        && node_modules/.bin/ng test  admin  --no-watch \
        && node_modules/.bin/ng test  app    --no-watch \
        && node_modules/.bin/ng test  common --no-watch ) >/tmp/nobilis-verify-front.log 2>&1; then
      fail=1; msg="$msg"$'\n'"[FRONT] verify RED (/tmp/nobilis-verify-front.log):"$'\n'"$(tail -n 30 /tmp/nobilis-verify-front.log)"
    fi
  fi
}

check "$root"
[ -n "$sibling" ] && check "$sibling"

[ "$fail" -eq 0 ] && exit 0
printf '%s\n' "STOP BLOCKED: build is RED (blocking once — fix and finish; a persistently red build surfaces and stops, not loops).$msg" >&2
exit 2
