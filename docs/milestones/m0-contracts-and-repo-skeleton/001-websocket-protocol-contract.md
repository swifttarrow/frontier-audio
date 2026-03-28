# Spec 001: WebSocket protocol contract

## Summary

Define the **client ↔ voice gateway** WebSocket protocol: JSON **control frames** for session lifecycle and errors, **binary audio** uplink/downlink framing, and **idempotent turn** correlation via `clientTurnId`. This document is the source of truth for m1 and m4 implementations.

**Plan:** [Phase 0](../../1-mvp.md#phase-0-contracts-and-repository-skeleton) · **Spec:** `docs/specs/1-initial.md` §6

## Scope

### In scope

- URL scheme (e.g. `wss://host/v1/voice`), subprotocol or version header if used
- Control message **envelope** (`type`, `payload`, optional `requestId`)
- `session.start` request/response fields: `deviceId` (opaque string from client), optional `clientVersion`; response: `sessionId`, `sessionToken` (opaque), `capabilities` (object), `repoDisplayName` (string, non-secret label for configured repo)
- Audio uplink: **binary** frames meaning (e.g. raw PCM 16-bit mono 16 kHz **or** Opus packets—**pick one in this doc and stick to it**)
- End-of-utterance signal (dedicated JSON message vs zero-length binary rule—document exactly one)
- `audio.frame` or equivalent batching rules (max frame size, ordering)
- Downlink: JSON events (`transcript.partial` optional, `transcript.final`, `assistant.text` optional, `tts.start`, `tts.chunk` binary, `tts.end`) **or** simplified v1 subset—list mandatory v1 messages
- `interrupt` control message (client → server): no body or `{}`; server MUST cancel TTS/generation for active turn
- `session.end` optional graceful close
- **Error** JSON shape: `code` (string), `message` (string), `retryable` (boolean), optional `details`
- **`clientTurnId`**: UUID string; required on utterance-bound control messages; server dedupes duplicate processing

### Out of scope

- Implementing the server handler (m1)
- TLS certificate provisioning
- Compression negotiation beyond what is fixed in v1

## Dependencies

- **Prior specs:** None
- **External:** None

## Interfaces & contracts

### Public API — WebSocket

**Connection:** `GET wss://{host}/v1/voice` (path is an example; document final path in the contract file).

**Control messages (JSON text frames)**

All control messages MUST be valid JSON objects with:

| Field | Type | Required |
|-------|------|----------|
| `type` | string | yes |
| `payload` | object | yes (may be empty `{}`) |
| `requestId` | string (UUID) | optional, client-generated for RPC-style pairs |
| `clientTurnId` | string (UUID) | required where noted below |

**`session.start` (client → server)**

- `payload.deviceId`: string, stable per install (see m4)
- `payload.clientVersion`: string (e.g. `1.0.0`)

**`session.start` ack (server → client)**

- `type`: `session.ack`
- `payload.sessionId`: UUID string
- `payload.sessionToken`: opaque string; client stores and sends on reconnect if spec’d
- `payload.capabilities`: object (e.g. `stt: true`, `tts: true`)
- `payload.repoDisplayName`: human-readable; MUST NOT embed secrets

**`interrupt` (client → server)**

- `type`: `interrupt`
- `payload`: `{}`
- `clientTurnId`: omit or use last active turn per implementation doc—**choose one rule and document**

**`session.end` (client → server)**

- `type`: `session.end`
- `payload`: `{}`

**End of utterance (client → server)** — document ONE of:

- `type`: `audio.commit` with `payload.clientTurnId`, or
- Binary sentinel described in binary section

**Binary frames**

- Document endianness, sample rate, channels, and whether each binary frame has a leading 1-byte **kind** discriminator (recommended for future codecs).

### Data & config

- Protocol version constant: e.g. `JARVIS_WS_V1` in docs and mirrored in code as integer `1`

## Behavior

### Acceptance criteria

1. A reader can implement a client and server **without** choosing unspecified encodings for v1 audio.
2. Every operation listed in `docs/specs/1-initial.md` §6 “Representative operations” maps to a concrete message type or binary rule in `docs/contracts/websocket-protocol.md`.
3. Error envelopes use `code`, `message`, `retryable` keys exactly (optional `details`).
4. `clientTurnId` semantics for idempotent turn processing are defined (e.g. second `audio.commit` with same id is no-op or returns same result reference).

### Edge cases & errors

- Malformed JSON → server sends `error` message with `code: "protocol.invalid"` and closes connection or recovers per documented policy
- Unsupported `type` → `error` with `code: "protocol.unknown_type"`
- Auth/token failure after m1 adds validation → `error` with `retryable: false` unless token refresh path exists

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `docs/contracts/websocket-protocol.md` | Canonical protocol document |

## Verification

### Automated

- [ ] N/A for doc-only spec; optional: markdown link checker in CI (m5)

### Manual

- [ ] Peer review: m1/m4 owners sign off that implementation can proceed without clarification

## Notes

- Prefer **one** audio codec for v1 to avoid Android encoder fragmentation; PCM 16 kHz mono is simplest at cost of bandwidth.
