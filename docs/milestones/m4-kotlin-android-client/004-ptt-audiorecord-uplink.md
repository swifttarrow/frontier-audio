# Spec 004: Press-to-hold AudioRecord and uplink streaming

## Summary

Implement **hold-to-talk**: while PTT pressed, read from **AudioRecord** at the **sample rate/channels** mandated by m0 (e.g. 16 kHz mono PCM16), chunk into frames, send **binary** WS frames, on release generate **`clientTurnId`** (UUID), send **`audio.commit`** with that id, then await assistant audio/text events.

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client) · **Research:** `docs/research/2026-03-26-mobile-audio-lifecycle-and-app-model.md`

## Scope

### In scope

- `AudioCapturePipeline` started/stopped by UI gesture
- Buffer size calculation via `AudioRecord.getMinBufferSize`
- Request **runtime** `RECORD_AUDIO` before first capture
- Map `AudioRecord` errors to user-visible non-text state + optional spoken error from server later

### Out of scope

- Oboe native pipeline (only if AudioRecord fails criteria)
- Background recording

## Dependencies

- **Prior specs:** [m4/003](./003-websocket-client-and-framing.md), [m4/001](./001-android-gradle-compose-permissions.md)
- **External:** Microphone hardware

## Interfaces & contracts

### Public API

```kotlin
interface AudioCapturePipeline {
  fun startCapture()
  fun stopCaptureAndCommit(): String // returns clientTurnId
}
```

- Emits chunks to `VoiceGatewayClient.audioFrames()`

## Behavior

### Acceptance criteria

1. While holding PTT, server receives monotonically increasing audio (integration test with mock server counting bytes).
2. On release, exactly **one** `audio.commit` with UUID per press cycle.
3. If permission denied, pipeline does not crash; UI shows denied state (m4-006).

### Edge cases & errors

- Rapid tap (<200ms): define whether commit sends silence or ignored—**document** (recommend ignore if no frames)

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/app/src/main/.../audio/AudioCapturePipeline.kt` | Mic |
| Modify | `AndroidManifest.xml` | `RECORD_AUDIO` already from m4-001 |

## Verification

### Automated

- [ ] Instrumented test with fake audio source if possible; else unit-test state machine

### Manual

- [ ] Speak on device; server STT returns plausible text in m1–m3

## Notes

- Acquire **audio focus** for capture if required on some OEMs (coordinate with m4-005).
