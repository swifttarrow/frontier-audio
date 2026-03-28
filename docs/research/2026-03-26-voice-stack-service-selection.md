# Research: Voice Stack Service Selection

## Question

Which voice stack services and transport model best match the v1 product and its strict grounding requirements?

## System Understanding

- Voice is the product surface, but trust is the product promise.
- The assistant needs fast acknowledgement and spoken answers, not open-ended conversation.
- Because answers must stay grounded in repository evidence, transcript visibility matters.

## Architecture Options

### Option A: OpenAI Chained Voice Stack

- STT with `gpt-4o-transcribe`
- Text reasoning and answer composition with a structured text model
- TTS with `gpt-4o-mini-tts`

Strengths:

- Strong control and transparency.
- Matches the strict grounding policy.
- Easier to log transcripts, evidence maps, and refusals.

Weaknesses:

- More orchestration work than end-to-end speech-to-speech.

### Option B: OpenAI Speech-to-Speech Realtime

- Realtime multimodal audio session over WebRTC or WebSocket.

Strengths:

- Best conversational feel.
- Strong low-latency potential.

Weaknesses:

- Harder to make the answer contract fully inspectable.
- Less natural fit for explicit evidence gating.

### Option C: Mixed Providers

- Separate STT, LLM, and TTS vendors.

Strengths:

- Maximum vendor flexibility.

Weaknesses:

- More integration cost.
- Slower path to first delivery.

## Recommendation

Use a chained voice stack for v1.

### Why

- OpenAI's own voice guidance recommends a chained architecture when you want predictable behavior, transcripts, and structured interactions.
- That maps directly to a GitHub retrieval assistant whose top risk is unsupported synthesis, not lack of conversational flair.
- WebRTC-first speech-to-speech can remain a later experiment if latency targets prove impossible with the chained path.

## Initial Service Picks

- STT: `gpt-4o-transcribe`
- Answer composition: text model with structured JSON output
- TTS: `gpt-4o-mini-tts`
- Client transport for v1: simple request/response or light streaming
- Realtime experiment track: WebRTC only after the text-grounded pipeline is validated

## Fast Experiments

1. Measure the latency of the chained path on a realistic mobile network.
2. Compare transcript quality and refusal correctness between chained and realtime prototypes.
3. Test whether TTS startup time or retrieval time is the dominant latency bottleneck.

## Sources

- [Voice agents (OpenAI)](https://platform.openai.com/docs/guides/voice-agents)
- [Realtime API with WebRTC (OpenAI)](https://platform.openai.com/docs/guides/realtime-webrtc)
