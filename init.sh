#!/usr/bin/env bash
#
# init.sh — the verification gate for coding agents (and humans).
#
# Runs exactly what CI (.github/workflows/test.yml) gates on, so "green here" means "green in CI":
# assemble the debug APK and run the JVM unit tests. It also fails if the merged manifest gained a
# network permission (CLAUDE.md, hard constraint 1).
#
# Usage:
#   ./init.sh          # build + unit tests + manifest check
#   ./init.sh test     # unit tests only (fast)
#
# Exit code is non-zero if any step fails.

set -euo pipefail
cd "$(dirname "$0")"

# --- JDK 21 ---------------------------------------------------------------------------------
jdk_major() {
  local home="$1"
  [[ -n "$home" ]] || return 1
  local java="$home/bin/java"; [[ -x "$java" || -x "$java.exe" ]] || return 1
  "$java" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | cut -d. -f1
}

find_jdk21() {
  local c major
  local -a candidates=()
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  if [[ -x /usr/libexec/java_home ]]; then
    c="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"; [[ -n "$c" ]] && candidates+=("$c")
  fi
  candidates+=("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
  candidates+=("/c/Program Files/Android/Android Studio/jbr")
  candidates+=("${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current")
  for c in "${candidates[@]}"; do
    major="$(jdk_major "$c" || true)"
    if [[ "$major" == "21" ]]; then printf '%s\n' "$c"; return 0; fi
  done
  return 1
}

if JDK21="$(find_jdk21)"; then
  export JAVA_HOME="$JDK21"
else
  echo "warning: no JDK 21 found; using whatever 'java' is on PATH" >&2
fi

# --- Gate -------------------------------------------------------------------------------------
cd android
case "${1:-all}" in
  test)
    ./gradlew testDebugUnitTest --console=plain
    ;;
  all|"")
    ./gradlew assembleDebug testDebugUnitTest --console=plain
    manifest="$(find build/intermediates/merged_manifest/debug -name AndroidManifest.xml | head -1)"
    if grep -q -E 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)' "$manifest"; then
      echo "FAIL: merged manifest requests network access (CLAUDE.md hard constraint 1): $manifest" >&2
      exit 1
    fi
    echo "OK: build, unit tests, and no network permission."
    ;;
  *)
    echo "usage: ./init.sh [all|test]" >&2; exit 2
    ;;
esac
