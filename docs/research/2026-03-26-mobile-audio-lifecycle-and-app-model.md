# Research: Mobile Audio Lifecycle and App Model

## Question

What mobile audio model best fits a mobile-first, push-to-talk, GitHub-only voice assistant with strict grounding and low-latency requirements?

## PRD Context

- The MVP is mobile-first, push-to-talk, GitHub-only voice Q&A.
- Wake word, passive listening, multi-user, memory, and agentic actions are out of scope for v1.
- The product must feel fast, interruptible, and trustworthy.

## System Understanding

- The core interaction loop is press to talk, record, transcribe, retrieve, answer, and speak back.
- The hardest product requirement is not UI polish; it is reliable audio capture and interruption handling on mobile.
- Industrial usage raises noise, route-change, and hands-busy constraints, but v1 still runs in the foreground.
- The product benefits from a transcript because retrieval, grounding, and refusal behavior all need inspection.
- The audio architecture should optimize for foreground reliability rather than background cleverness.

## Hard Problems

- Audio interruptions: iOS deactivates the session during interruptions and expects the app to restore state afterward.
- Audio focus: Android increasingly restricts who can request audio focus and when microphone access is valid.
- Permission timing: microphone permissions on Android are while-in-use sensitive, which punishes background complexity.
- Latency budget: audio transport, transcription, retrieval, answer composition, and TTS all compete inside a tight turn time.
- Playback control: barge-in must stop playback immediately without leaving audio state corrupted.

## Architecture Options

### Option A: Foreground PTT + Chained Voice Pipeline

- Client records audio only while the app is foregrounded and the user is actively pressing to talk.
- Client uploads audio chunks or a completed utterance to the backend.
- Backend runs STT, retrieval, answer composition, and TTS as a text-centered pipeline.
- Client plays synthesized audio and can immediately stop playback on user interrupt.

Strengths:

- Highest control over grounding and refusals.
- Easier to log transcripts and evidence maps.
- Lowest product risk for v1.
- Works with cross-platform mobile frameworks.

Weaknesses:

- Slightly less conversational than native speech-to-speech.
- More moving parts across STT, retrieval, LLM, and TTS.
- Requires careful tuning to keep latency low.

### Option B: Client-Side Realtime Speech-to-Speech

- Client uses a realtime voice session directly with a speech-to-speech model.
- Retrieval hooks and tool calls provide repository data during the conversation.
- Audio input and output stay in a low-latency session.

Strengths:

- Best raw turn-taking feel.
- Low-latency conversational flow.
- Reduced glue code in some flows.

Weaknesses:

- Harder to enforce strict quote-or-restate grounding.
- Harder to inspect and validate every spoken claim.
- Tool orchestration and evidence attribution become more delicate.

### Option C: Native Audio Core with Background-Ready Foundations

- Build the audio stack natively in Swift and Kotlin from the beginning.
- Prepare the codebase for future background audio and more advanced voice modes.
- Keep the same chained backend model as Option A.

Strengths:

- Best long-term control over audio edge cases.
- Strongest path if v2 later needs deep platform integration.
- Lowest abstraction risk around interruptions and routing.

Weaknesses:

- Highest initial delivery cost.
- Slower iteration for an MVP whose UI is intentionally thin.
- Duplicated implementation effort across iOS and Android.

## Deep Decisions

### Decision: Foreground vs Background Recording

Option A:
- Foreground-only PTT.

Option B:
- Background-capable recording foundation.

Tradeoffs:
- Foreground-only is simpler, lower-risk, and aligned with scope.
- Background recording triggers service, permission, and battery complexity that v1 does not need.

Recommendation:
- Choose foreground-only PTT for v1.

### Decision: Chained vs Speech-to-Speech

Option A:
- Chained pipeline: STT -> retrieval/orchestration -> answer model -> TTS.

Option B:
- Speech-to-speech realtime model.

Tradeoffs:
- Chained gives a transcript and explicit control, which fits strict grounding.
- Speech-to-speech improves fluidity but weakens inspectability.

Recommendation:
- Use a chained voice architecture for v1, even if a realtime speech mode is explored later.

### Decision: Client Framework Pressure on Audio

Option A:
- Cross-platform framework with native escape hatches.

Option B:
- Fully native apps.

Tradeoffs:
- Cross-platform is enough for foreground PTT if audio APIs are mature.
- Fully native only becomes clearly worth it if background audio, wake word, or deep route control become core scope.

Recommendation:
- Keep the audio requirements inside the bounds of a cross-platform-capable MVP.

## Recommendation

The initial delivery architecture should be:

- Foreground-only push-to-talk on mobile.
- Chained audio pipeline, not speech-to-speech.
- Explicit transcript and evidence-bearing answer composition on the backend.
- Immediate local playback stop on interrupt.
- No background recording, no wake word, and no persistent microphone activity.

If using OpenAI for the voice stack, the most aligned starting chain is:

- STT: `gpt-4o-transcribe`
- Answer model: a text model with structured output and citation discipline
- TTS: `gpt-4o-mini-tts`

## Fast Experiments

1. Measure end-to-end latency for a foreground PTT turn on a mid-range Android device and a recent iPhone.
2. Test interruption recovery for phone-call-like audio focus loss on both platforms.
3. Verify that a single tap-and-hold or tap-to-start/tap-to-stop interaction is comfortable with gloves and noisy surroundings.

## Initial Technology Picks to Pressure-Test

- Client model: mobile foreground PTT only
- Voice architecture: chained
- Transport: regular HTTPS upload first, streaming later if needed
- Audio abstraction target: cross-platform framework with native module escape hatch

## Sources

- [Responding to Interruptions (Apple)](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/HandlingAudioInterruptions/HandlingAudioInterruptions.html)
- [Manage audio focus (Android)](https://developer.android.com/media/optimize/audio-focus)
- [Restrictions on starting a foreground service from the background (Android)](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [MediaRecorder overview (Android)](https://developer.android.com/media/platform/mediarecorder)
- [Voice agents (OpenAI)](https://platform.openai.com/docs/guides/voice-agents)
- [Realtime API with WebRTC (OpenAI)](https://platform.openai.com/docs/guides/realtime-webrtc)
