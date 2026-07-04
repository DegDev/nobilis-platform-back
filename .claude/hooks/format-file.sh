#!/usr/bin/env bash
# PostToolUse hook: format the just-edited file in place. Side-effect only — it
# must NEVER fail an edit (the tool has already run), so every path exits 0.
# Dispatch by extension: *.java -> standalone google-java-format jar (provisioned
# under .claude/tools/, gitignored); *.ts/*.tsx/*.html/*.scss/*.css -> the repo's
# node_modules/.bin/prettier. Byte-identical across both repos.
set -uo pipefail   # NOT -e: a formatter error must never fail the edit

file="$(cat | jq -r '.tool_input.file_path // empty')"
[ -z "$file" ] && exit 0
case "$file" in /*) ;; *) exit 0 ;; esac   # Edit/Write always pass an absolute path; skip anything else (a relative path would hang the .git walk below)
[ -f "$file" ] || exit 0

# Walk up to the repo root (dir containing .git) so tool resolution is root-agnostic.
dir="$(dirname -- "$file")"; root=""
while [ "$dir" != "/" ]; do
  [ -e "$dir/.git" ] && { root="$dir"; break; }
  dir="$(dirname -- "$dir")"
done
[ -z "$root" ] && exit 0

# Resolve a JDK >= 21: google-java-format 1.35.0 needs javac internals absent from
# the box-default JDK 17 (NoClassDefFoundError: JCTree$JCAnyPattern). Portable, no
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

# Extensions are deliberately LIMITED. Do NOT add *.md/*.json — prettier on the
# methodology/sources docs would churn them and break byte-identical-across-repos.
case "$file" in
  *.java)
    jar="$(ls "$root"/.claude/tools/google-java-format-*-all-deps.jar 2>/dev/null | head -n1)"
    [ -f "$jar" ] || exit 0                       # jar not provisioned -> skip (CI/spotless still gate)
    jdk="$(find_jdk)" || exit 0                    # no JDK >= 21 -> skip
    "$jdk" \
      --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
      --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
      --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
      --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
      --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
      -jar "$jar" --replace "$file" 2>/dev/null || true
    ;;
  *.ts|*.tsx|*.html|*.scss|*.css)
    pf="$root/node_modules/.bin/prettier"
    [ -x "$pf" ] || exit 0                         # prettier not installed here -> skip
    "$pf" --write -- "$file" 2>/dev/null || true
    ;;
esac
exit 0
