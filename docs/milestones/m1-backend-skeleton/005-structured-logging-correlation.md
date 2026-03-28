# Spec 005: Structured logging with sessionId and turnId

## Summary

Ensure every **voice pipeline** log line (and HTTP access logs where applicable) carries **`sessionId`** and **`turnId`** (when known) as structured fields, and **redact** secrets (`sessionToken`, PATs, full GitHub URLs with tokens). Satisfies `docs/specs/1-initial.md` §9 observability baseline for m1.

**Plan:** [Phase 1](../../1-mvp.md#phase-1-backend-skeleton)

## Scope

### In scope

- JSON logging layout: `timestamp`, `level`, `message`, `sessionId`, `turnId`, `logger`
- MDC / coroutine context propagation for `sessionId` from WebSocket connection through STT/TTS calls
- Redaction filter for substrings matching `Bearer `, `ghp_`, `GITHUB_TOKEN`, query params `?token=`

### Out of scope

- Metrics / Prometheus (m5)
- OpenTelemetry (m5 optional)

## Dependencies

- **Prior specs:** [m1/003](./003-websocket-session-handshake.md), [m1/004](./004-audio-pipeline-stt-tts-echo.md)
- **External:** Logging backend (stdout is enough)

## Interfaces & contracts

### Public API

- `withLoggingContext(sessionId: UUID, turnId: UUID?, block: suspend () -> Unit)` (or equivalent)

### Data & config

| Env | Purpose |
|-----|---------|
| `LOG_LEVEL` | default INFO |

## Behavior

### Acceptance criteria

1. Grepping logs for a `sessionId` shows full STT/TTS path for that session.
2. No raw `sessionToken` appears at INFO or WARN.
3. Failed STT includes `sessionId` and `clientTurnId` on error log.

### Edge cases & errors

- Unknown `turnId` before commit: log only `sessionId`

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../observability/Logging.kt` | MDC + redaction |
| Modify | `services/voice-gateway/src/main/resources/logback.xml` or framework config | JSON encoder |

## Verification

### Automated

- [ ] Test: log statement with fake token in message is redacted

### Manual

- [ ] Run one WS session; confirm JSON lines in stdout

## Notes

- If using Python, `structlog` + processor for redaction.
