# Research: Client Runtime Choice

## Question

What client runtime, language, and service shape best fit the v1 mobile assistant?

## System Understanding

- v1 is a mobile-first foreground PTT client with minimal UI.
- The hardest client concerns are audio capture, interruptions, permissions, playback control, and fast iteration.
- The product does not currently need background recording or wake word.

## Runtime Options

### Option A: Expo / React Native with Development Builds

- Client written in TypeScript.
- Use Expo modules and development builds rather than relying only on Expo Go.
- Use `expo-audio` or custom native modules if required.

Strengths:

- Fastest cross-platform iteration.
- Shared language with a TypeScript backend.
- Mature enough for foreground audio flows.

Weaknesses:

- Some low-level audio cases may still require custom native code.
- Realtime audio and platform-edge behavior must be tested early.

### Option B: Bare React Native

- Client written in TypeScript.
- Greater direct control over native modules and dependencies.

Strengths:

- Good cross-platform balance with fewer Expo abstractions.
- Easier to add lower-level packages immediately.

Weaknesses:

- More setup and maintenance cost than Expo-led development.

### Option C: Fully Native Swift + Kotlin

- Separate native apps.

Strengths:

- Maximum platform control.
- Best long-term fit if the roadmap returns to advanced audio modes.

Weaknesses:

- Slowest MVP delivery.
- Two codebases for a product whose first surface is intentionally small.

### Option D: Mobile Web / Web Wrapper

- Browser or webview-first client.

Strengths:

- Lowest distribution overhead.

Weaknesses:

- Weakest microphone and audio lifecycle control.
- Worst fit for reliable mobile voice UX.

## Recommendation

Start with Expo / React Native development builds.

Reasoning:

- v1 is foreground-only, which keeps the audio problem inside what modern Expo and React Native can plausibly handle.
- TypeScript on client and server improves iteration speed.
- Expo's New Architecture support is mature enough for a new app, and `expo-audio` gives a credible starting point for recording and playback.
- If audio edge cases exceed the abstraction, Expo still provides a path to native modules instead of forcing a full rewrite.

## Initial Technology Picks

- Client runtime: Expo / React Native development builds
- Client language: TypeScript
- Backend language: TypeScript
- Backend framework family: lightweight HTTP API plus worker process
- Database: PostgreSQL + `pgvector`
- Search: Postgres full-text + vector hybrid
- Voice stack direction: chained audio pipeline

## Services Worth Prototyping Early

- Mobile audio: `expo-audio`
- Voice models: OpenAI chained STT + text + TTS path
- GitHub access: Octokit on the server
- Data store: managed Postgres with `pgvector`

## What Would Change This Recommendation

- If v2 brings back passive listening or deep background audio requirements, re-evaluate fully native clients.
- If WebRTC and custom audio routing become first-class product requirements early, pressure-test bare React Native versus native sooner.

## Fast Experiments

1. Build a one-screen PTT prototype in Expo development builds and verify interruption behavior on both platforms.
2. Measure end-to-end latency using the proposed chained backend.
3. Identify whether any required audio behavior is blocked by Expo before committing deeper.

## Sources

- [Expo New Architecture](https://docs.expo.dev/guides/new-architecture/)
- [Expo Audio](https://docs.expo.dev/versions/latest/sdk/audio/)
- [PermissionsAndroid (React Native)](https://reactnative.dev/docs/permissionsandroid)
