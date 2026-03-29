#!/usr/bin/env bash
# Ensures ANDROID_HOME before running Gradle in apps/android (used by Makefile).
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
  :
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
elif [[ -d "${HOME}/Library/Android/sdk" ]]; then
  export ANDROID_HOME="${HOME}/Library/Android/sdk"
elif [[ -d "${HOME}/Android/Sdk" ]]; then
  export ANDROID_HOME="${HOME}/Android/Sdk"
else
  echo "Android SDK not found. Install Android Studio or the command-line tools, then either:" >&2
  echo "  export ANDROID_HOME=\"\$HOME/Library/Android/sdk\"   # macOS (default Studio path)" >&2
  echo "  export ANDROID_HOME=\"\$HOME/Android/Sdk\"          # Linux (default Studio path)" >&2
  echo "Or set sdk.dir in apps/android/local.properties (see Android Studio)." >&2
  exit 1
fi

# AGP 8.2 + Gradle 8.5: run Gradle on JDK 17–21 (JDK 22+ often fails; JDK 25 is unsupported).
export GRADLE_JAVA_MAX_MAJOR=21

exec "$script_dir/with-gradle-jdk.sh" "$@"
