#!/usr/bin/env bash
# Create database jarvis_dev in Compose Postgres if it is missing.
# The official image only runs POSTGRES_DB init on an empty data volume; reused volumes
# can end up with user "jarvis" but no jarvis_dev database.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! docker compose exec -T postgres pg_isready -U jarvis -q 2>/dev/null; then
  echo "Jarvis Postgres (docker compose) is not running or not ready. From repo root: docker compose up -d" >&2
  exit 1
fi

if docker compose exec -T postgres psql -U jarvis -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='jarvis_dev'" | grep -qx 1; then
  exit 0
fi

echo "Creating database jarvis_dev in Compose Postgres…" >&2
# Use template0 so we do not clone template1 (avoids collation-version errors on some reused/broken volumes).
docker compose exec -T postgres psql -U jarvis -d postgres -v ON_ERROR_STOP=1 -c \
  "CREATE DATABASE jarvis_dev WITH TEMPLATE template0 OWNER jarvis;"
