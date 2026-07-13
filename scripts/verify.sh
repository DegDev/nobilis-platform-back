#!/usr/bin/env bash
# Single-sourced verify: runs the full build+test check for the tree at $1 (default:
# cwd), branching on pom.xml (back/Maven) vs angular.json (front/Angular+Vitest).
# Called both by the Stop hooks (which add the porcelain/docs-only scoping and the
# block-once/exit-2 semantics around this) and directly for a manual, independent
# verify — same commands either way, single source of truth.
set -uo pipefail

repo="${1:-.}"
log="${2:-/tmp/nobilis-verify.log}"

# Resolve a JDK >= 25 for the Java-25 Maven build (box default is 17). Portable, no
# hardcoded path: prefer $JAVA_HOME, then the newest sdkman candidate, then PATH. Maven's own
# host JVM is fixed by JAVA_HOME/PATH before Maven reads any of its own config (toolchains,
# jvm.config, maven.config), so this is the only place that can actually gate the build --
# checkstyle runs in-process (not toolchain-aware) and fails to even load its classes below
# JDK 21, while the release-25 compile needs a real 25 javac. Fails loudly (non-zero, stderr
# message) rather than silently falling through to a bare `mvn` on an inadequate JVM.
find_jdk() {
  local c v
  for c in "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
           $(ls -d "$HOME"/.sdkman/candidates/java/*/bin/java 2>/dev/null | sort -rV) \
           "$(command -v java 2>/dev/null || true)"; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    v="$("$c" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    case "$v" in ''|*[!0-9]*) continue ;; esac
    [ "$v" -ge 25 ] && { printf '%s\n' "$c"; return 0; }
  done
  printf 'verify.sh: JDK 25+ required for this build, none found -- install one (e.g. `sdk install java 25-tem`) or check .sdkmanrc\n' >&2
  return 1
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

if [ -f "$repo/pom.xml" ]; then
  jdk="$(find_jdk)" || exit 1
  jh="$(dirname "$(dirname "$jdk")")"
  # Force UTF-8 regardless of the caller's locale (e.g. POSIX/C with LANG unset):
  # a locale-driven JVM file.encoding of ANSI_X3.4-1968 degrades Cyrillic/Romanian
  # test assertions to "?" and false-reds tests that are otherwise correct.
  export LANG="${LANG:-C.UTF-8}" LC_ALL="${LC_ALL:-C.UTF-8}"
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
  run_mvn() { ( cd "$repo" && { [ -n "$jh" ] && export JAVA_HOME="$jh"; mvn -B "$@"; } ); }
  # Retry-once-on-red: a stale target/ (e.g. an annotation-processor-generated
  # .imports entry surviving a rename/delete under incremental compilation) can
  # fail a build for reasons unrelated to this session's diff. Pay for `clean`
  # only when verify is ALREADY red, not on every green run (bounded retry).
  if run_mvn verify >"$log" 2>&1; then
    exit 0
  fi
  run_mvn clean verify >"$log" 2>&1
  exit $?
elif [ -f "$repo/angular.json" ]; then
  node_bin="$(find_node "$repo" || true)"
  if [ -z "$node_bin" ]; then
    wanted="$(tr -d '[:space:]' < "$repo/.nvmrc" 2>/dev/null)"
    printf '[FRONT] node %s not found, skipping front verify\n' "${wanted:-?}" >&2
    exit 0
  fi
  node_dir="$(dirname "$node_bin")"
  ( cd "$repo" && export PATH="$node_dir:$PATH" \
      && node_modules/.bin/ng build common --configuration=development \
      && node_modules/.bin/ng build admin  --configuration=development \
      && node_modules/.bin/ng build app    --configuration=development \
      && node_modules/.bin/ng test  admin  --no-watch \
      && node_modules/.bin/ng test  app    --no-watch \
      && node_modules/.bin/ng test  common --no-watch ) >"$log" 2>&1
  exit $?
else
  printf 'verify.sh: %s has neither pom.xml nor angular.json, nothing to verify\n' "$repo" >&2
  exit 0
fi
