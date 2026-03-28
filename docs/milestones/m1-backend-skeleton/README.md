# Milestone 1: Backend skeleton

## Overview

Implement **session issuance** (device-bound token), **WebSocket endpoint**, **audio receive → STT → echo or fixed phrase TTS** (no LLM tools yet), structured **logging** (`sessionId`, `turnId`), and **PostgreSQL** schema for `DeviceSession` + minimal `ConversationTurn` (`docs/specs/1-initial.md` §5).

**Plan reference:** [Phase 1: Backend skeleton](../../1-mvp.md#phase-1-backend-skeleton)

## Dependencies

- [ ] Milestone 0 — `docs/contracts/websocket-protocol.md` finalized; repo layout + Makefile exist

## Changes Required

- WebSocket handler implementing m0 protocol subset for `session.start`, audio uplink, `interrupt`, `session.end`
- DB migrations: `device_sessions`, `conversation_turns`
- STT + TTS integrations (chained stack per research); env-based secrets
- `IntegrationConfig` via env or DB row: `defaultPublicRepoUrl` for later m2

## Success Criteria

### Automated Verification

- [ ] `make test` — session creation, message parsing, idempotent `clientTurnId` handling
- [ ] `make lint` — backend formatter/linter passes

### Manual Verification

- [ ] Test client opens WS, sends mock audio, receives transcript + TTS response path (may be logged PCM file if TTS returns bytes)
- [ ] Logs include `sessionId`, `turnId`; no raw secrets or sensitive URLs in default log level

## Implementation specs

- [001-database-migrations-sessions-turns](./001-database-migrations-sessions-turns.md)
- [002-integration-config-default-repo](./002-integration-config-default-repo.md)
- [003-websocket-session-handshake](./003-websocket-session-handshake.md)
- [004-audio-pipeline-stt-tts-echo](./004-audio-pipeline-stt-tts-echo.md)
- [005-structured-logging-correlation](./005-structured-logging-correlation.md)
