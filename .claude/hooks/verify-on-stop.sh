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

fail=0; msg=""
check() {
  local repo="$1" jdk jh
  [ -d "$repo" ] || return 0
  [ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ] || return 0   # clean tree -> skip
  if [ -f "$repo/pom.xml" ]; then
    jdk="$(find_jdk || true)"; jh=""
    [ -n "$jdk" ] && jh="$(dirname "$(dirname "$jdk")")"
    if ! ( cd "$repo" && { [ -n "$jh" ] && export JAVA_HOME="$jh"; mvn -B verify; } ) >/tmp/nobilis-verify-back.log 2>&1; then
      fail=1; msg="$msg"$'\n'"[BACK] mvn -B verify RED (/tmp/nobilis-verify-back.log):"$'\n'"$(tail -n 30 /tmp/nobilis-verify-back.log)"
    fi
  elif [ -f "$repo/angular.json" ]; then
    if ! ( cd "$repo" \
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
