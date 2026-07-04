#!/usr/bin/env bash
# One-time provisioning of the standalone google-java-format jar used by the
# PostToolUse format hook. Run manually after clone/pull:
#   .claude/hooks/bootstrap-formatter.sh
# The version is read from pom.xml so the jar ALWAYS matches spotless — the
# spotless:check goal in `mvn verify` (CI) is the real gate; this just keeps
# per-edit formatting in lockstep with it. The jar lands under .claude/tools/
# (gitignored, never committed).
set -euo pipefail

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
ver="$(sed -nE 's:.*<google-java-format\.version>([^<]+)</google-java-format\.version>.*:\1:p' "$root/pom.xml" | head -1)"
[ -n "$ver" ] || { echo "Could not read google-java-format.version from $root/pom.xml" >&2; exit 1; }

tools="$root/.claude/tools"; mkdir -p "$tools"
jar="$tools/google-java-format-$ver-all-deps.jar"
[ -f "$jar" ] && { echo "Already present: $jar"; exit 0; }

url="https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/$ver/google-java-format-$ver-all-deps.jar"
echo "Downloading $url"
curl -fsSL -o "$jar" "$url"
echo "Provisioned $jar"
