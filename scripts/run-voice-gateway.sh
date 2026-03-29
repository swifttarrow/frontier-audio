#!/usr/bin/env bash
# Load repo-root .env into the environment, then start voice-gateway.
# JVM/Kotlin only sees real env vars — .env is not read automatically.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# If Compose Postgres is up, ensure jarvis_dev exists (stale volumes skip POSTGRES_DB init).
if docker compose exec -T postgres pg_isready -U jarvis -q 2>/dev/null; then
  ./scripts/ensure-jarvis-db.sh
fi

exec ./scripts/with-gradle-jdk.sh services/voice-gateway ./gradlew run
