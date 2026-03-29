#!/usr/bin/env bash
# Picks a JDK for the Gradle launcher. Optional GRADLE_JAVA_MAX_MAJOR caps the version
# (Android uses 21; voice-gateway allows newer when unset — Gradle 9.4+ runs on JDK 25+).
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <project-dir> <command> [args...]" >&2
  exit 2
fi

proj=$1
shift

java_major_of_home() {
  local home=$1 line maj
  [[ -x "$home/bin/java" ]] || return 1
  line=$("$home/bin/java" -version 2>&1 | head -1)
  if [[ $line =~ version\ \"1\.([0-9]+) ]]; then
    maj=${BASH_REMATCH[1]}
  elif [[ $line =~ version\ \"([0-9]+) ]]; then
    maj=${BASH_REMATCH[1]}
  else
    return 1
  fi
  [[ "$maj" =~ ^[0-9]+$ ]] || return 1
  echo "$maj"
}

java_major_from_home() {
  local home=$1 maj
  maj=$(java_major_of_home "$home") || return 1
  if (( maj >= 17 )); then
    return 0
  fi
  return 1
}

java_home_acceptable() {
  local home=$1 maj max="${GRADLE_JAVA_MAX_MAJOR:-}"
  maj=$(java_major_of_home "$home") || return 1
  (( maj >= 17 )) || return 1
  [[ -n "$max" ]] && (( maj > max )) && return 1
  return 0
}

try_export_java_home() {
  local h
  for h in "$@"; do
    [[ -n "$h" ]] || continue
    if java_home_acceptable "$h"; then
      export JAVA_HOME="$h"
      return 0
    fi
  done
  return 1
}

# Pick highest installed JDK on macOS that is >= 17 and (if set) <= GRADLE_JAVA_MAX_MAJOR.
pick_java_home_from_java_home_V() {
  [[ -x /usr/libexec/java_home ]] || return 1
  local best_maj=0 best_home= line maj home max
  max="${GRADLE_JAVA_MAX_MAJOR:-999}"
  while IFS= read -r line; do
    [[ $line =~ ^[[:space:]]+([0-9]+)\. ]] || continue
    maj=${BASH_REMATCH[1]}
    [[ "$maj" =~ ^[0-9]+$ ]] || continue
    [[ $line =~ (/[[:alnum:]_/.-]+)$ ]] || continue
    home="${BASH_REMATCH[1]}"
    [[ -x "$home/bin/java" ]] || continue
    (( maj >= 17 && maj <= max && maj > best_maj )) || continue
    best_maj=$maj
    best_home=$home
  done < <(/usr/libexec/java_home -V 2>&1)
  if (( best_maj > 0 )); then
    export JAVA_HOME="$best_home"
    return 0
  fi
  return 1
}

# Homebrew keg-only JDKs are often not registered with /usr/libexec/java_home.
try_export_java_home \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /opt/homebrew/opt/openjdk@23/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@23/libexec/openjdk.jdk/Contents/Home \
  || true

if [[ -z "${JAVA_HOME:-}" ]]; then
  pick_java_home_from_java_home_V || true
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  jh=$(dirname "$(dirname "$(command -v java)")")
  if java_home_acceptable "$jh"; then
    export JAVA_HOME="$jh"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]] || ! java_home_acceptable "${JAVA_HOME:-}"; then
  line=$(java -version 2>&1 | head -1 || true)
  if [[ -n "${GRADLE_JAVA_MAX_MAJOR:-}" ]]; then
    echo "This Gradle project needs JDK 17–${GRADLE_JAVA_MAX_MAJOR} (Android Gradle Plugin). Your default java is ${line:-unknown}." >&2
    echo "Install a supported JDK, e.g.: brew install openjdk@21" >&2
    echo "Then run: export JAVA_HOME=\"\$(/usr/libexec/java_home -v 21)\"" >&2
    echo "Or use: ./scripts/with-android-env.sh apps/android ./gradlew <task>" >&2
  else
    echo "Gradle needs JDK 17 or newer. Your default java is ${line:-unknown}." >&2
    echo "On macOS (Homebrew): brew install openjdk@17 (JDK 25 works with Gradle 9.4+ for voice-gateway)." >&2
    echo "This script prefers /opt/homebrew/opt/openjdk@17 (and @21, @23) when present." >&2
  fi
  exit 1
fi

cd "$proj"
exec "$@"
