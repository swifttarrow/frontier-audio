# Milestone 4: Kotlin Android client

## Overview

Ship **Jetpack Compose** app with **one press-to-talk** control, **EncryptedSharedPreferences** or **DataStore** for `deviceId` + `sessionToken`, **WebSocket** client per m0 protocol, **AudioRecord** hold-to-talk uplink, **playback** with **audio focus**, **interrupt** via **tap during playback** (per plan assumption), and **no required EditText** for core flows.

**Plan reference:** [Phase 4: Kotlin Android client](../../1-mvp.md#phase-4-kotlin-android-client)

## Dependencies

- [ ] Milestone 0 — WebSocket contract documented
- [ ] Milestone 1 — live gateway for E2E (can develop UI against stub earlier)

## Changes Required

- Gradle module, Compose theme minimal, `RECORD_AUDIO` permission
- OkHttp or Ktor WebSocket; reconnect backoff
- `BuildConfig.VOICE_GATEWAY_WS_URL` from `local.properties` or env-injected CI

## Success Criteria

### Automated Verification

- [x] `make test` / `./gradlew test` — protocol framing + state machine unit tests
- [x] `make lint` / `./gradlew lint` or ktlint

### Manual Verification

- [ ] Hold PTT → speak → release → hear response
- [ ] Tap PTT during playback → stop; next hold works
- [ ] Airplane mode → non-crashing error state
- [ ] No required keyboard input on main flow

## Implementation specs

- [001-android-gradle-compose-permissions](./001-android-gradle-compose-permissions.md)
- [002-secure-device-session-storage](./002-secure-device-session-storage.md)
- [003-websocket-client-and-framing](./003-websocket-client-and-framing.md)
- [004-ptt-audiorecord-uplink](./004-ptt-audiorecord-uplink.md)
- [005-tts-playback-audio-focus-interrupt](./005-tts-playback-audio-focus-interrupt.md)
- [006-compose-ptt-main-screen](./006-compose-ptt-main-screen.md)
