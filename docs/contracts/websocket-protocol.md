# Jarvis WebSocket Protocol — v1

**Protocol version:** `JARVIS_WS_V1` (integer `1`)

---

## Connection

```
GET wss://{host}/v1/voice
```

No subprotocol header required for v1. TLS is mandatory in production.

---

## Framing rules

- **Text frames** carry JSON control messages.
- **Binary frames** carry audio data (see [Binary audio](#binary-audio)).
- Each JSON text frame MUST be a single, complete JSON object — no streaming JSON.

---

## Control message envelope

All JSON messages conform to:

```jsonc
{
  "type": "<string>",          // required — message type
  "payload": { ... },          // required — may be empty {}
  "requestId": "<uuid>",       // optional — client-generated, echoed in response pairs
  "clientTurnId": "<uuid>"     // required where noted per message type
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | always | Identifies the message kind |
| `payload` | object | always | Message-specific data; `{}` if none |
| `requestId` | string (UUID) | optional | Client-generated; server echoes in paired responses |
| `clientTurnId` | string (UUID) | where noted | Idempotent turn key — see [Idempotency](#idempotency) |

---

## Client -> Server messages

### `session.start`

Sent once after WebSocket open. Server MUST NOT accept audio before acknowledging.

```jsonc
{
  "type": "session.start",
  "payload": {
    "deviceId": "<string>",       // stable per install
    "clientVersion": "1.0.0"      // semver
  },
  "requestId": "<uuid>"           // optional
}
```

### `audio.commit`

Signals end of utterance. All binary audio frames sent since the last `audio.commit` (or session start) belong to this turn.

```jsonc
{
  "type": "audio.commit",
  "payload": {},
  "clientTurnId": "<uuid>"        // required — unique per utterance
}
```

### `interrupt`

Requests the server to cancel any in-progress TTS generation and streaming for the current turn.

```jsonc
{
  "type": "interrupt",
  "payload": {},
  "clientTurnId": "<uuid>"        // the turn being interrupted (last active turn)
}
```

Server behavior on `interrupt`:
1. Stop TTS generation if still running.
2. Discard queued `tts.chunk` frames not yet sent.
3. Send `tts.end` with `interrupted: true` if a TTS stream was active.
4. Session is ready for the next PTT cycle.

### `session.end`

Graceful close. Server flushes pending writes and closes the WebSocket.

```jsonc
{
  "type": "session.end",
  "payload": {}
}
```

---

## Server -> Client messages

### `session.ack`

Response to `session.start`.

```jsonc
{
  "type": "session.ack",
  "payload": {
    "sessionId": "<uuid>",
    "sessionToken": "<opaque-string>",
    "capabilities": {
      "stt": true,
      "tts": true
    },
    "repoDisplayName": "org/repo-name"   // human-readable; MUST NOT embed secrets
  },
  "requestId": "<uuid>"                  // echoed from session.start if provided
}
```

The client MUST store `sessionToken` for potential reconnection.

### `transcript.partial`

Optional incremental STT result (may be omitted in v1).

```jsonc
{
  "type": "transcript.partial",
  "payload": {
    "text": "<partial transcript>"
  },
  "clientTurnId": "<uuid>"
}
```

### `transcript.final`

Final STT transcript for the utterance.

```jsonc
{
  "type": "transcript.final",
  "payload": {
    "text": "<final transcript>"
  },
  "clientTurnId": "<uuid>"
}
```

### `assistant.text`

Optional text of the assistant's response (useful for logging/display).

```jsonc
{
  "type": "assistant.text",
  "payload": {
    "text": "<assistant response text>"
  },
  "clientTurnId": "<uuid>"
}
```

### `tts.start`

Signals the beginning of TTS audio for a turn.

```jsonc
{
  "type": "tts.start",
  "payload": {
    "format": "pcm_16k_16bit_mono",
    "sampleRate": 16000,
    "channels": 1,
    "bitsPerSample": 16
  },
  "clientTurnId": "<uuid>"
}
```

### `tts.chunk`

Delivered as **binary frames** (see [Binary audio — Downlink](#downlink-server--client)).

### `tts.end`

Signals TTS stream completion for a turn.

```jsonc
{
  "type": "tts.end",
  "payload": {
    "interrupted": false          // true if ended due to interrupt
  },
  "clientTurnId": "<uuid>"
}
```

### `error`

```jsonc
{
  "type": "error",
  "payload": {
    "code": "<string>",          // e.g. "protocol.invalid", "stt.failed"
    "message": "<human-readable>",
    "retryable": true,           // boolean
    "details": {}                // optional additional context
  },
  "requestId": "<uuid>",        // echoed if error relates to a request
  "clientTurnId": "<uuid>"       // present if error relates to a turn
}
```

**Standard error codes:**

| Code | Retryable | Description |
|------|-----------|-------------|
| `protocol.invalid` | false | Malformed JSON or missing required fields |
| `protocol.unknown_type` | false | Unrecognized `type` value |
| `session.invalid_token` | false | Token validation failed |
| `session.not_started` | false | Message received before `session.ack` |
| `stt.failed` | true | Speech-to-text processing error |
| `tts.failed` | true | Text-to-speech processing error |
| `orchestrator.failed` | true | LLM / tool calling error |
| `rate_limit.exceeded` | true | Per-device/IP rate limit hit |

On `protocol.invalid` or `protocol.unknown_type`, the server MAY close the connection after sending the error.

---

## Binary audio

### Uplink (client -> server)

| Property | Value |
|----------|-------|
| Encoding | Linear PCM, signed 16-bit, little-endian |
| Sample rate | 16,000 Hz |
| Channels | 1 (mono) |
| Frame header | 1-byte kind discriminator (value `0x01` = PCM audio) |
| Max frame size | 32,000 bytes payload (1 second of audio) |
| Byte order | Little-endian samples |

Each binary WebSocket frame:
```
[0x01] [PCM samples...]
 ^kind  ^audio payload
```

The 1-byte kind discriminator allows future codec negotiation without breaking v1 clients.

**Ordering:** Frames MUST be sent in capture order. The server processes them sequentially per connection.

### Downlink (server -> client)

TTS audio is sent as binary WebSocket frames with the same structure:

| Property | Value |
|----------|-------|
| Encoding | Linear PCM, signed 16-bit, little-endian |
| Sample rate | 16,000 Hz |
| Channels | 1 (mono) |
| Frame header | 1-byte kind discriminator (value `0x01` = PCM audio) |

Downlink binary frames are only valid between `tts.start` and `tts.end` control messages.

---

## Idempotency

`clientTurnId` is a UUID generated by the client, unique per utterance.

- The server MUST track processed `clientTurnId` values for the session lifetime.
- If the server receives an `audio.commit` with a previously seen `clientTurnId`, it MUST NOT re-process the utterance. It MAY return a reference to the original result or silently acknowledge.
- `interrupt` references the `clientTurnId` of the turn being interrupted.

---

## Session lifecycle

```
Client                              Server
  |                                    |
  |--- [WS open] ------------------->  |
  |--- session.start ---------------->  |
  |<-- session.ack -------------------|
  |                                    |
  |--- [binary audio frames] -------->  |  (PTT held)
  |--- audio.commit ----------------->  |  (PTT released)
  |<-- transcript.final --------------|
  |<-- tts.start ---------------------|
  |<-- [binary TTS chunks] ----------|
  |<-- tts.end -----------------------|
  |                                    |
  |--- [binary audio frames] -------->  |  (next turn)
  |--- audio.commit ----------------->  |
  |<-- transcript.final --------------|
  |<-- tts.start ---------------------|
  |<-- [binary TTS chunks] ----------|
  |--- interrupt -------------------->  |  (user taps during playback)
  |<-- tts.end (interrupted: true) ---|
  |                                    |
  |--- session.end ------------------>  |
  |<-- [WS close] --------------------|
```

---

## Reconnection

If the WebSocket drops unexpectedly:

1. Client reconnects to `wss://{host}/v1/voice`.
2. Client sends `session.start` with the same `deviceId`.
3. Server issues a new `sessionId` (sessions are not resumed in v1).
4. Client discards in-flight `clientTurnId` values and starts fresh.

Future versions may support session resumption using `sessionToken`.

---

## Spec traceability

| Spec §6 operation | Protocol message(s) |
|--------------------|---------------------|
| `session.start` | `session.start` + `session.ack` |
| `audio.frame` / end-of-utterance | Binary frames + `audio.commit` |
| `interrupt` | `interrupt` + `tts.end(interrupted: true)` |
| `session.end` | `session.end` + WS close |
| Error envelope | `error` with `code`, `message`, `retryable` |
| Idempotent turn | `clientTurnId` on `audio.commit` / `interrupt` |
