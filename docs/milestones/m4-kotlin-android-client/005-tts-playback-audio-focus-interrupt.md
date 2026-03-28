# Spec 005: TTS playback, audio focus, interrupt

## Summary

Play **downlink TTS** using **AudioTrack** (PCM) or **ExoPlayer** if server sends encoded audio—**match m0 codec**. Request **AUDIOFOCUS_GAIN** (or transient) during playback; on **`interrupt`** message send from UI, **stop** playback immediately, **flush** buffers, and notify `VoiceGatewayClient.sendInterrupt()`.

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client) · **Assumption:** tap PTT during playback = interrupt (`docs/plans/1-mvp.md`)

## Scope

### In scope

- `TtsPlaybackController`: `playPcm(stream: Flow<ByteArray>)`, `stop()`
- Handle Bluetooth routing via default stream usage `USAGE_MEDIA` or `USAGE_VOICE_COMMUNICATION`—**pick one** and document
- Wire gateway `tts.start` / binary chunks / `tts.end` into controller
- When user taps PTT while `Speaking` state: call `stop()` then `sendInterrupt()`

### Out of scope

- Background playback
- Visual waveform

## Dependencies

- **Prior specs:** [m4/003](./003-websocket-client-and-framing.md)
- **External:** None

## Interfaces & contracts

### Public API

```kotlin
interface TtsPlaybackController {
  fun onTtsStart()
  fun onTtsChunk(bytes: ByteArray)
  fun onTtsEnd()
  fun stopImmediately()
}
```

## Behavior

### Acceptance criteria

1. Interrupt during playback stops audio within **200ms** (manual test guideline).
2. After interrupt, next hold-to-talk starts clean new capture without stale audio.
3. Incoming phone call (audio focus loss) pauses playback; resume policy documented (optional stop).

### Edge cases & errors

- Partial chunk after `tts.end` → ignored

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/app/src/main/.../audio/TtsPlaybackController.kt` | Playback |

## Verification

### Automated

- [ ] Unit test: `stopImmediately` clears internal queue

### Manual

- [ ] Bluetooth headset playback + interrupt

## Notes

- If server sends MP3/Opus, ExoPlayer is simpler than raw AudioTrack.
