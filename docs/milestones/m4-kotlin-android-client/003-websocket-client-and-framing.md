# Spec 003: WebSocket client, JSON control frames, binary audio framing

## Summary

Implement **WebSocket** connectivity to `BuildConfig.VOICE_GATEWAY_WS_URL` using **OkHttp** or **Ktor** client: send **`session.start`** with `deviceId` on connect, handle **`session.ack`**, parse **`error`** envelopes, send **`interrupt`** and **`session.end`**, and marshal **binary** audio per `docs/contracts/websocket-protocol.md`.

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client)

## Scope

### In scope

- `VoiceGatewayClient` with states: `Disconnected`, `Connecting`, `Ready`, `Error`
- Exponential backoff reconnect on disconnect (max delay cap e.g. 30s)
- JSON serialization with **kotlinx.serialization** or Moshi
- Binary frame writer/reader matching server (codec from m0)
- Coroutine `Flow` of **server events** for UI layer (`SessionAck`, `Error`, `TtsChunk`, etc.)

### Out of scope

- Certificate pinning (m5 optional)

## Dependencies

- **Prior specs:** [m0/001](../m0-contracts-and-repo-skeleton/001-websocket-protocol-contract.md), [m4/002](./002-secure-device-session-storage.md)
- **External:** Network permission; TLS stack from platform

## Interfaces & contracts

### Public API

```kotlin
interface VoiceGatewayClient {
  suspend fun connect()
  fun disconnect()
  suspend fun sendSessionStart()
  suspend fun sendInterrupt()
  suspend fun sendAudioCommit(clientTurnId: String)
  fun audioFrames(): SendChannel<ByteArray> // or callback API — document
  val events: Flow<GatewayEvent>
}

sealed interface GatewayEvent {
  data class SessionAck(val sessionId: String, val token: String, val repoDisplayName: String) : GatewayEvent
  data class Error(val code: String, val message: String, val retryable: Boolean) : GatewayEvent
  // TTS events per m0
}
```

## Behavior

### Acceptance criteria

1. Against m1 server: connect → `session.start` → receive `session.ack` → store token in `SessionStore`.
2. Malformed server message yields `GatewayEvent.Error` with `code` propagated.
3. Reconnect after network drop resends `session.start` with same `deviceId` (new or same `sessionId` per server policy—document).

### Edge cases & errors

- WebSocket failure during send → surface `Error(retryable=true)`

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/app/src/main/.../net/VoiceGatewayClient.kt` | WS |
| Create | `apps/android/app/src/main/.../net/GatewayEvent.kt` | Types |

## Verification

### Automated

- [ ] MockWebServer test: handshake JSON sequence
- [ ] Unit test: binary frame packing matches m0 examples

### Manual

- [ ] Logcat shows ack after connect

## Notes

- Use single-threaded dispatcher for WebSocket sends to preserve ordering.
