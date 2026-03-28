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

```bash
cd services/voice-gateway
gradle wrapper --gradle-version 8.5   # first time only
./gradlew run
```

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
| `JARVIS_DEFAULT_GITHUB_REPO_URL` | No | anthropics/claude-code | Default public repo |
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

Default URL: `postgresql://jarvis:jarvis@localhost:5432/jarvis_dev`

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
