# Spec 004: Audio pipeline — receive, STT, echo TTS

## Summary

Wire **binary audio uplink** to **STT**, persist **user turn** text, run a **minimal response** (echo transcript or fixed phrase), synthesize **TTS** audio, and stream **downlink** binary frames per m0 protocol. Implements the m1 **vertical slice** without LLM tools (m2 adds tools).

**Plan:** [Phase 1](../../1-mvp.md#phase-1-backend-skeleton) · **Research:** `docs/research/2026-03-26-voice-stack-service-selection.md`

## Scope

### In scope

- Accept audio frames after `session.start` until **`audio.commit`** (or documented end signal)
- Call STT provider (e.g. OpenAI transcribe or equivalent) with **same codec** as m0 doc
- Create `conversation_turns` row: `role=user`, `text=<transcript>`, `client_turn_id` from client
- Generate assistant reply text: `Echo: <transcript>` OR fixed “Jarvis ready.” — **pick one for tests**
- Call TTS; emit downlink messages: at minimum `tts.start`, binary chunks, `tts.end` (exact names per m0)
- **Idempotency:** second `audio.commit` with same `clientTurnId` returns same assistant turn without duplicate STT charge (detect existing row)

### Out of scope

- LLM reasoning, tools, memory (m2–m3)
- Partial streaming STT to client (optional nice-to-have)

## Dependencies

- **Prior specs:** [m1/003](./003-websocket-session-handshake.md), [m0/001](../m0-contracts-and-repo-skeleton/001-websocket-protocol-contract.md)
- **External:** `OPENAI_API_KEY` or vendor keys; HTTP client with timeouts

## Interfaces & contracts

### Public API (internal modules)

```text
interface SpeechToText {
  suspend fun transcribe(pcmAudio: ByteArray, mimeOrFormat: String): Result<String>
}

interface TextToSpeech {
  suspend fun synthesize(text: String, voice: String?): Result<ByteArray>
}
```

- Errors map to WS `error` or spoken fallback policy—**for m1**, WS `error` with `retryable: true` is acceptable

### Data & config

| Env | Purpose |
|-----|---------|
| `STT_MODEL` | optional default |
| `TTS_MODEL` / `TTS_VOICE` | optional default |

## Behavior

### Acceptance criteria

1. End-to-end: valid PCM/Opus clip → `conversation_turns` has user + assistant rows for same `clientTurnId`.
2. Idempotent `clientTurnId`: no duplicate user turns; second commit gets same response reference (test asserts single STT call via mock).
3. `interrupt` during TTS: server stops emitting further `tts.*` for active turn (best-effort).

### Edge cases & errors

- STT returns empty string → assistant says “I didn’t catch that” via TTS or error message—document choice
- Audio too large → reject with `error` `audio.too_large`

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../audio/AudioIngestService.kt` | Buffers frames until commit |
| Create | `services/voice-gateway/src/.../audio/SttClient.kt` | Vendor adapter |
| Create | `services/voice-gateway/src/.../audio/TtsClient.kt` | Vendor adapter |
| Create | `services/voice-gateway/src/.../audio/TurnPipeline.kt` | Orchestrates STT → persist → TTS → emit |

## Verification

### Automated

- [ ] Tests with fake STT/TTS: deterministic transcript in, audio bytes out
- [ ] Idempotency test on `clientTurnId`

### Manual

- [ ] Record short clip from phone or ffmpeg piped through test client; hear echo in client or saved WAV

## Notes

- Chained stack keeps transcripts in logs for later grounding audits (m2).
