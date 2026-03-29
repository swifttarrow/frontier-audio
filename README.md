# Jarvis

Voice-first assistant that answers from a provided operational API and public GitHub data, with cross-session memory and interruptible playback.

## Architecture

```
[Android App]  --WSS-->  [Voice Gateway (Ktor)]  --> [OpenAI STT/TTS]
   PTT UI                   |  |  |                  [OpenAI LLM + Tools]
   AudioRecord              |  |  |
   AudioTrack               |  |  +--> [GitHub REST API]
                            |  +-----> [Operational API (stub/real)]
                            +--------> [PostgreSQL: sessions, turns, memory]
```

## Repository layout

```
apps/
  android/              Kotlin Android client (Jetpack Compose)
services/
  voice-gateway/        Kotlin/Ktor backend: WS gateway, STT/TTS, orchestration
docs/
  contracts/            Protocol and adapter contracts
  eval/                 MVP evaluation scenarios
  specs/                Technical specification
  plans/                Implementation plans
  milestones/           Milestone task breakdowns
  research/             Research documents
```

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17+ | Required for voice-gateway and Android build |
| Android SDK | API 26+ | Required for Android client |
| Docker | 20+ | Optional, for local PostgreSQL |
| Make | any | Build orchestration |

## Quick start

### 1. Start local PostgreSQL

```bash
docker compose up -d
```

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and set at minimum:
- `OPENAI_API_KEY` — for STT, TTS, and LLM
- `GITHUB_TOKEN` — optional, for higher GitHub API rate limits

### 3. Run the voice gateway

From the **repository root** (`frontier-audio/`), use the helper script so variables from `.env` are exported into the process (the JVM does not load `.env` on its own):

```bash
./scripts/run-voice-gateway.sh
```

If your shell is in `services/voice-gateway/`, use `../../scripts/run-voice-gateway.sh` instead (paths are relative to the repo root).

Alternatively, export env vars yourself, then run Gradle:

```bash
set -a && source .env && set +a
cd services/voice-gateway && ./gradlew run
```

(The repo includes the Gradle wrapper; JDK 17+ must be on your `PATH`, or use `./scripts/with-gradle-jdk.sh` as in the script above.)

The server starts on `http://localhost:8080` with:
- `GET /health` — health check
- `GET /metrics` — Prometheus-format metrics
- `WSS /v1/voice` — voice WebSocket endpoint
- `DELETE /v1/devices/{deviceId}/data` — admin data deletion

### 4. Run the Android app

```bash
cd apps/android
echo "voiceGatewayWsUrl=ws://10.0.2.2:8080/v1/voice" > local.properties
./gradlew installDebug
```

Use `10.0.2.2` for the Android emulator (maps to host localhost).

### 5. Run tests

```bash
make test    # all subprojects
make lint    # all subprojects
```

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | No | - | PostgreSQL connection string |
| `OPENAI_API_KEY` | Yes* | - | OpenAI API key for STT/TTS/LLM |
| `GITHUB_TOKEN` | No | - | GitHub PAT for higher rate limits |
| `JARVIS_DEFAULT_GITHUB_REPO_URL` | No | - | If set, new sessions start with this public `owner/repo`; if unset, the assistant asks for a GitHub user and repo in conversation |
| `OPERATIONAL_API_BASE_URL` | No | - | Unset = fake adapter |
| `OPERATIONAL_FAKE_MODE` | No | normal | normal/empty/error/stale |
| `LLM_MODEL` | No | gpt-4o | LLM model for orchestration |
| `RETENTION_DAYS` | No | 30 | Data retention period |
| `ADMIN_API_KEY` | No | - | Key for admin endpoints |
| `LOG_LEVEL` | No | INFO | Logging level |

\* Without `OPENAI_API_KEY`, the server runs in echo mode with fake STT/TTS.

## Local PostgreSQL

```bash
docker compose up -d        # start
docker compose down          # stop (data preserved)
docker compose down -v       # stop and wipe all data
```

Default URL: `postgresql://jarvis:jarvis@localhost:5433/jarvis_dev` (Compose publishes the DB on **host port 5433** so it does not fight with a local Postgres on 5432.)

**`FATAL: role "jarvis" does not exist`:** Usually `DATABASE_URL` still points at `localhost:5432`, which is often **not** this project’s container (for example Homebrew Postgres). Use port **5433** in `DATABASE_URL`, run `docker compose up -d` from the repo root, and recreate the stack if you changed the port mapping after an older `docker compose up`.

**`FATAL: database "jarvis_dev" does not exist`:** Often a **reused Docker volume** was initialized without that database (the image only creates `POSTGRES_DB` on first empty startup). `./scripts/run-voice-gateway.sh` tries to create it automatically when Compose Postgres is running; you can also run `./scripts/ensure-jarvis-db.sh` by hand (it uses `template0` to avoid broken `template1` collation metadata).

If you still see **collation version** / **template1** errors from Postgres, the volume’s cluster metadata is inconsistent (common after upgrades or odd Docker setups). Reset it: `docker compose down -v && docker compose up -d` (this deletes local DB data for this compose project).

If you intentionally use another Postgres instance, set `DATABASE_URL` to that server’s host, port, user, and database.

## Evaluation

See [docs/eval/mvp-scenarios.md](docs/eval/mvp-scenarios.md) for the full evaluation checklist covering voice loop, truthfulness, integration, and error handling scenarios.

## Contracts

- [WebSocket protocol](docs/contracts/websocket-protocol.md)
- [Operational API adapter](docs/contracts/operational-api-adapter.md)

## Documentation

- [PRD](docs/prd.md)
- [Technical spec](docs/specs/1-initial.md)
- [Implementation plan](docs/plans/1-mvp.md)
- [Milestones](docs/milestones/_index.md)

## Known limitations

- **No private repos** — only public GitHub repositories via configured URL
- **No keyboard input** — all interaction is voice-only by design
- **Single-tenant** — one device identity per session, no multi-user auth
- **Echo mode** — without `OPENAI_API_KEY`, responses are simple echoes
- **Operational API** — uses fake/stub adapter until real API spec is delivered
