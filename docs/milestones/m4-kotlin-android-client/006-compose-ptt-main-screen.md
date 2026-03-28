# Spec 006: Compose main screen — single PTT and non-text states

## Summary

Build the **main** Compose screen: **one** large **press-to-talk** button (no `TextField` / `EditText` on required path), visual **state** labels or icons for **Idle / Listening / Thinking / Speaking / Error**, runtime **permission** flow for microphone, and coordination of **`VoiceGatewayClient`**, **`AudioCapturePipeline`**, **`TtsPlaybackController`**.

**Plan:** [Phase 4](../../1-mvp.md#phase-4-kotlin-android-client) · **Spec:** `docs/specs/1-initial.md` stakeholder UX

## Scope

### In scope

- `ViewModel` exposing `UiState` and handling:
  - PTT **press down** → connect if needed, `startCapture()`, state `Listening`
  - PTT **release** → `stopCaptureAndCommit()`, state `Thinking` until first TTS or error
  - Gateway events update state; TTS drives `Speaking`
  - **Tap** while `Speaking` → `stopImmediately()` + `sendInterrupt()` → `Idle`
- Accessibility: `contentDescription` on PTT; `semantics` for state changes
- No settings screen required for MVP (URLs from BuildConfig)

### Out of scope

- On-screen keyboard for any required user input
- Multi-screen navigation

## Dependencies

- **Prior specs:** [m4/003](./003-websocket-client-and-framing.md), [m4/004](./004-ptt-audiorecord-uplink.md), [m4/005](./005-tts-playback-audio-focus-interrupt.md)
- **External:** None

## Interfaces & contracts

### UI

- `MainScreen(viewModel: MainViewModel = hiltViewModel() /* or koin */)`

```kotlin
data class MainUiState(
  val phase: VoicePhase,
  val statusLabel: String, // short, e.g. "Listening"
  val errorMessage: String?
)

enum class VoicePhase { Idle, Listening, Thinking, Speaking, Error }
```

## Behavior

### Acceptance criteria

1. Core flow never focuses an `EditText`; grep `EditText` / `OutlinedTextField` in `main` source → **zero** for required screens (debug-only OK if guarded).
2. PTT uses **single** composable root (one `Button`/`Surface` with pointerInput).
3. Error state shows **icon + short text**; user dismisses via tapping PTT to retry (no keyboard).

### Edge cases & errors

- Double-connect: ViewModel guards single active client

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `apps/android/app/src/main/.../ui/MainScreen.kt` | UI |
| Create | `apps/android/app/src/main/.../ui/MainViewModel.kt` | State |

## Verification

### Automated

- [ ] Compose UI test: PTT press/release toggles mocked ViewModel states

### Manual

- [ ] Full E2E with backend: scenarios from plan Phase 4 manual checklist

## Notes

- Optional: haptic on `Listening` start for “eyes-up” feedback.
