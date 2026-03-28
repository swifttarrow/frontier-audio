# Spec 003: WebSocket session handshake and connection lifecycle

## Summary

Implement the **WebSocket** server endpoint that accepts connections, handles **`session.start`**, issues **`session.ack`** with `sessionId` and opaque `sessionToken`, persists **`DeviceSession`**, and rejects malformed protocol messages with structured **`error`** frames per m0 contract.

**Plan:** [Phase 1](../../1-mvp.md#phase-1-backend-skeleton) · **Contract:** `docs/contracts/websocket-protocol.md`

## Scope

### In scope

- Route: path as documented in m0 (e.g. `/v1/voice`)
- Parse JSON text frames; validate `type` and required fields
- On `session.start`: create session row, return `session.ack` with capabilities `{ stt: true, tts: true }` (or actual flags)
- Connection scoped to **one** `sessionId` for MVP
- `session.end`: mark session inactive, close WS gracefully
- `interrupt`: stub that clears **local** playback queue state if any server-side buffer exists in m1 (full cancel in m2+); must not crash

### Out of scope

- Token reconnection flow (optional: document “client must `session.start` again on reconnect” for v1)

## Dependencies

- **Prior specs:** [m0/001](../m0-contracts-and-repo-skeleton/001-websocket-protocol-contract.md), [m1/001](./001-database-migrations-sessions-turns.md), [m1/002](./002-integration-config-default-repo.md)
- **External:** None

## Interfaces & contracts

### Public API — WebSocket

- **Upgrade:** standard HTTP → WebSocket
- **Auth:** MVP none beyond `deviceId` binding; optional header `Authorization: Bearer <sessionToken>` on reconnect—if not implemented, omit from v1 doc

**Server → client errors**

```json
{
  "type": "error",
  "payload": {
    "code": "session.invalid_device",
    "message": "deviceId required",
    "retryable": false
  }
}
```

## Behavior

### Acceptance criteria

1. Client sends `session.start` with valid `deviceId` → receives `session.ack` with UUID `sessionId` and non-empty `sessionToken`.
2. Duplicate `session.start` on same connection: **define** behavior (ignore second vs error) and document in code + tests.
3. Malformed JSON yields `error` with `protocol.invalid` or similar.
4. `session.ack` includes `repoDisplayName` from IntegrationConfig.

### Edge cases & errors

- DB down on `session.start` → `error` with `retryable: true`, close connection

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../ws/VoiceWebSocketHandler.kt` | Routing |
| Create | `services/voice-gateway/src/.../ws/SessionManager.kt` | In-memory connection ↔ session |

## Verification

### Automated

- [ ] Unit/integration tests with WebSocket client: happy path + malformed payload

### Manual

- [ ] `wscat` or small script completes handshake

## Notes

- Ktor: `webSocket` block; FastAPI: `fastapi` + `websockets` library—mirror structure.
