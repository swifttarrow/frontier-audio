# Jarvis

Voice-first assistant that answers from a provided operational API, public GitHub data, and optional web search, with cross-session memory and interruptible playback.

This repository is the **frontier-audio** monorepo (Android client + Kotlin voice gateway).

## Architecture

```
[Android App]  --WSS-->  [Voice Gateway (Ktor)]  --> [OpenAI STT/TTS]
   PTT UI                   |  |  |                  [OpenAI LLM + Tools]
   AudioRecord              |  |  |
   AudioTrack               |  |  +--> [GitHub REST API]
                            |  |  +--> [Tavily API (optional: web_search)]
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
- `TAVILY_API_KEY` — optional; without it, the `web_search` tool reports that search is not configured ([Tavily](https://tavily.com/))

See [.env.example](.env.example) for all keys and defaults.

### 3. Run the voice gateway

From the **repository root** (`frontier-audio/`), use the helper script so variables from `.env` are exported into the process:

```bash
./scripts/run-voice-gateway.sh
```

If your shell is in `services/voice-gateway/`, use `../../scripts/run-voice-gateway.sh` instead (paths are relative to the repo root).

The gateway also **reads a repo-root `.env` itself** when you run `ApplicationKt` from the IDE or `./gradlew run` without sourcing: it walks up from the current working directory until it finds `.env`. Real process environment variables still take precedence. **Fine-grained GitHub PATs** must include the repositories you query and **read** access for metadata, issues, and pull requests; org repos often need **SSO authorize** on the token.

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

There is **no browser UI** for voice. Opening the gateway’s HTTPS origin in a tab is only useful for **operational HTTP routes** (for example `GET /health` or `GET /metrics`). The assistant runs over **`wss://…/v1/voice`** from the **Android app** (or any client that speaks the [WebSocket protocol](docs/contracts/websocket-protocol.md)).

### 4. Run the Android app

```bash
cd apps/android
cp local.properties.example local.properties
# Set sdk.dir to your Android SDK path (see comments in local.properties.example).
# voiceGatewayWsUrl defaults to the emulator host mapping below.
./gradlew installDebug
```

`local.properties.example` sets `voiceGatewayWsUrl=ws://10.0.2.2:8080/v1/voice` — use `10.0.2.2` on the **emulator** (host loopback). On a **physical device**, replace it with your machine’s LAN IP (for example `ws://192.168.x.x:8080/v1/voice`).

### 5. Run tests and linters

```bash
make test    # all subprojects
make lint    # all subprojects
```

The root `Makefile` also defines `make fmt` for future formatting; it is currently a no-op.

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | No | - | PostgreSQL connection string |
| `OPENAI_API_KEY` | Yes* | - | OpenAI API key for STT/TTS/LLM |
| `GITHUB_TOKEN` | No | - | GitHub PAT for higher rate limits |
| `GITHUB_CACHE_TTL_SECONDS` | No | 180 | In-memory TTL (seconds) for cached GitHub REST responses in the gateway |
| `JARVIS_DEFAULT_GITHUB_REPO_URL` | No | - | If set, new sessions start with this public `https://github.com/owner/repo` URL; if unset, the assistant asks for user and repo in conversation |
| `JARVIS_REPO_DISPLAY_NAME` | No | derived | When a default repo URL is set, optional `owner/repo` label for logs and UX (otherwise parsed from the URL) |
| `TAVILY_API_KEY` | No | - | Enables the `web_search` tool; if unset, that tool returns a configuration error |
| `OPERATIONAL_API_BASE_URL` | No | - | Unset = fake adapter |
| `OPERATIONAL_FAKE_MODE` | No | normal | normal/empty/error/stale |
| `LLM_MODEL` | No | gpt-5.4-nano | LLM model for orchestration |
| `STT_MODEL` | No | whisper-large-v3-turbo | Transcription model for `/v1/audio/transcriptions`; override if your account returns an invalid-model error (e.g. `whisper-1` or `gpt-4o-mini-transcribe`) |
| `STT_LANGUAGE` | No | - | Optional ISO 639-1 code (e.g. `en`) passed to the transcription API; reduces wrong-language transcripts when auto-detect mis-guesses on noisy or short audio |
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
- [Requirements (source brief)](docs/requirements.md)
- [Technical spec](docs/specs/1-initial.md)
- [Database model](docs/db-model.md)
- [Implementation plan](docs/plans/1-mvp.md)
- [Milestones](docs/milestones/_index.md)

## Known limitations

- **No web client** — no hosted HTML/JS app; voice is WebSocket-only (`/v1/voice`), intended for the Android client unless you build your own WS client
- **No private repos** — only public GitHub repositories via configured URL
- **No keyboard input** — all interaction is voice-only by design
- **Single-tenant** — one device identity per session, no multi-user auth
- **Echo mode** — without `OPENAI_API_KEY`, responses are simple echoes
- **Web search** — requires `TAVILY_API_KEY`; otherwise the tool is unavailable at runtime
- **Operational API** — uses fake/stub adapter until real API spec is delivered
