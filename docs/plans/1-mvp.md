# Jarvis Implementation Plan

**Produced with:** `agent/prompts/plan.md` (implementation plan from technical spec).

## Overview

Deliver **Jarvis** as specified in `docs/specs/1-initial.md`: a **Kotlin Android** app with a **single press-to-talk** control (no required soft-keyboard input), talking to a **backend** over **WebSocket** that runs **STT → orchestration (tools: GitHub + operational API) → TTS**, persists **device-scoped memory**, and enforces **grounding and ~3 minute freshness** awareness. This plan sequences greenfield work from contracts through hardening, aligned with the spec’s §12 phases.

## Tech spec traceability

- **Spec:** `docs/specs/1-initial.md`
- **Maps to spec sections:**
  - §3 System context, §4 Architecture → Phases 0–2, 4–5
  - §5 Data model → Phases 0–1, 3
  - §6 APIs and interfaces → Phases 0–1, 2, 4
  - §7 AuthN / AuthZ → Phases 1, 4
  - §8 Key workflows → Phases 1–4 (vertical slices)
  - §9 NFRs → Phase 5 (and incremental instrumentation from Phase 1)
  - §10 Risks → Assumptions below + Phase 5 evals
  - §11 Open questions → Resolved or **Assumption** + owner (below); none left blocking
  - §1–2 Summary / goals → Phases 1–5 (behavioral acceptance via manual scenarios in Phases 1–5)

### Coverage map (spec → work type)

| Spec area | Implied work |
|-----------|----------------|
| §1 Summary (voice loop, PTT, backend pipeline) | Phases 0–1 (protocol + skeleton), 4 (client), 2–3 (orchestration + memory) |
| §2 Goals (V/T/I families) | Phase 2 grounding tests, Phase 3 recall, Phase 4 interrupt + PTT, Phase 5 eval checklist |
| §2 Non-goals | Excluded from all phases; no auth/background/agentic PR work |
| §3–4 Context / architecture | Repo layout, service boundaries, secrets on server only (Phases 0–1) |
| §5 Data model | Migrations, entities `DeviceSession`, `ConversationTurn`, `MemoryChunk`, `IntegrationConfig` (Phases 1, 3) |
| §6 APIs (WS, GitHub, operational API) | Contract docs + gateway (0–1), adapters + caching (2), client WS (4) |
| §7 AuthN/Z | Device token issuance, secure client storage (1, 4) |
| §8 Workflows | Vertical slices validated in manual steps each phase; §8.2 interrupt in Phase 4 |
| §9 NFRs | Metrics, rate limits, audio focus, TLS (1 incremental, 4–5) |
| §10–11 Risks / open questions | Assumptions table; Phase 5 evals and policy hooks |

### Assumptions (spec §11 and gaps)

| Topic | Assumption | Owner |
|--------|------------|--------|
| GitHub URL without keyboard | **Default public repo URL** from server env / build-time server config; optional **deep link** later for evaluators—no QR in MVP unless time allows | Product + eng |
| Interrupt with one control | **Tap PTT while assistant is speaking** sends `interrupt` and stops playback; **hold** = record when idle | Design sign-off before Phase 4 exit |
| Operational API | **Stub OpenAPI + fake server** until shared spec lands; adapter interface is the contract | Eng |
| Kotlin scope | **Android-only**, **Jetpack Compose**, min SDK aligned with team policy (TBD: 26+ typical for audio) | Eng |
| Backend language | **Kotlin (Ktor)** HTTP + WebSocket gateway for symmetry with Android and one primary JVM stack **or** Python (FastAPI) if team standard—**pick one at Phase 0 kickoff**; spec does not mandate | Eng |
| Voice stack | **Chained** STT + text LLM + TTS per `docs/research/2026-03-26-voice-stack-service-selection.md` (inspectable transcripts, grounding-friendly) | Eng |
| Retention / privacy | **30-day default** retention for text turns + memory chunks in dev; production policy **pending** PRD §13 Q3—implement configurable TTL + manual session delete API | Product / legal |

## Current State Analysis

- **Repo:** Documentation, agent prompts, and **research** under `docs/research/`; **no application code**, no `Makefile`, no CI in tree.
- **Research vs spec:** `docs/research/2026-03-26-client-runtime-choice.md` recommends **Expo/React Native**; **approved spec** requires **Kotlin**—implementation follows **spec**; RN research is non-binding for client.
- **Gaps:** Operational API spec, evaluator deployment target (hosted vs local), final interrupt gesture, production retention—all covered by assumptions or Phase 0 tasks.

## Desired End State

- Android app: one **PTT** surface, mic permission, WebSocket client, streaming/recording upload, TTS playback, **interrupt**, minimal non-text states (listening / thinking / speaking).
- Backend: session + device token, WebSocket protocol (`session.start`, audio uplink, `interrupt`, TTS downlink), STT/TTS + LLM orchestration with **GitHub** and **operational API** tools, **PostgreSQL** (or equivalent) for sessions/turns/memory/config, structured logging and basic metrics.
- **Verification:** Documented manual scenarios (PTT, interrupt, memory recall, empty API/GitHub, rate limit messaging) plus automated tests where feasible; root **`make test`** and **`make lint`** aggregate subprojects.

## What We're NOT Doing

Mirrors spec §2 non-goals: private GitHub, passive/background listening, sign-in and multi-tenant isolation, preference rules, agentic PR creation, Swift client, rich visual chrome beyond PTT + status, **any required mobile keyboard input**.

---

## Phase 0: Contracts and repository skeleton

### Overview

Lock **WebSocket message schema** (JSON control + binary audio framing), **session/device auth handshake**, **operational API adapter interface** (with stub), **IntegrationConfig** shape (`defaultPublicRepoUrl`, `operationalApiBaseUrl`), and **repo layout** so later phases do not rework boundaries.

### Changes Required

- **New:** `docs/contracts/websocket-protocol.md` (or OpenAPI for HTTP + separate WS doc)—message types: `session.start`, `audio.frame` / end-of-utterance, `interrupt`, `session.end`, error envelopes, `clientTurnId`.
- **New:** `docs/contracts/operational-api-adapter.md`—methods, error mapping, `asOf`/cache timestamp requirement (spec §6).
- **New:** Root **`Makefile`** with `test` and `lint` targets (initially no-op or `echo` until subprojects exist; Phase 1 must wire real commands).
- **New:** Monorepo directories **as chosen at kickoff**, e.g. `apps/android/` (Gradle), `services/voice-gateway/` (Ktor or FastAPI)—**empty or hello-world** only.
- **New:** `.editorconfig` / `.gitignore` updates if missing for chosen stacks.
- **Optional:** `docker-compose.yml` for PostgreSQL local dev.

### Success Criteria

#### Automated Verification

- [ ] `make test` runs (may be no-op or placeholder until Phase 1)
- [ ] `make lint` runs (placeholder acceptable)

#### Manual Verification

- [ ] Reviewer can read contracts and trace each spec §6 operation to a message or HTTP route
- [ ] Open questions from spec §11 are reflected in assumptions table above or explicitly deferred with artifact link

**Note:** Pause for human confirmation after this phase before proceeding.

---

## Phase 1: Backend skeleton

### Overview

Implement **session issuance** (device-bound token), **WebSocket endpoint**, **audio receive → STT → echo or fixed phrase TTS** (no tools yet), structured **logging** (`sessionId`, `turnId`), and **PostgreSQL** schema for `DeviceSession` + minimal `ConversationTurn` (spec §5).

### Changes Required

- **Service:** WebSocket handler; persist session on `session.start`; validate token on reconnect if spec’d.
- **DB:** Migrations for `device_sessions`, `conversation_turns` (nullable fields for future artifacts).
- **Integrations:** STT + TTS SDKs per research (chained stack); configuration via env (secrets not committed).
- **Config:** `IntegrationConfig` row or env-backed config for `defaultPublicRepoUrl` (can point to a public fixture repo for dev).

### Success Criteria

#### Automated Verification

- [ ] `make test` — unit tests for session creation, message parsing, idempotent `clientTurnId` handling
- [ ] `make lint` — formatter/linter for chosen backend language passes

#### Manual Verification

- [ ] CLI or minimal test client can open WS, send mock audio or file, receive transcript + TTS response
- [ ] Logs include correlation IDs without logging raw secrets or full URLs in unsafe channels

**Note:** Pause for human confirmation after this phase before proceeding.

---

## Phase 2: Tools — GitHub and operational API

### Overview

Add **GitHub client** (public repo only) with rate-limit handling and **operational API client** via **stub first**, then real adapter when spec arrives. Wire **LLM tool calling** so answers use **only** tool outputs for factual claims; add **freshness** hints (~3 min) in prompts from cache timestamps (spec §5–6, §9).

### Changes Required

- **GitHub:** Read paths for issues/PRs/metadata per `docs/research/2026-03-26-github-integration-contract.md` where compatible with spec; cache with TTL ≤ freshness policy.
- **Operational API:** `OperationalApiAdapter` trait/interface; fake implementation returning golden fixtures; swap-in real HTTP client later.
- **Orchestrator:** System prompt + tool schemas enforcing no fabrication; tests with **empty** and **error** tool responses.
- **Errors:** Map to structured `code`, `message`, `retryable` (spec §6).

### Success Criteria

#### Automated Verification

- [ ] `make test` — integration tests against GitHub **public** test repo (or recorded fixtures) and fake API; golden tests for “no data” replies
- [ ] `make lint`

#### Manual Verification

- [ ] Spoken (or logged) path: user question → tool fetch → answer contains only grounded fields
- [ ] Simulated GitHub rate limit returns user-visible retry guidance (spec §9)

**Note:** Pause for human confirmation after this phase before proceeding.

---

## Phase 3: Memory

### Overview

Implement **MemoryChunk** persistence and retrieval keyed by **session/device**; after each turn (or batch), write summaries; inject memory into orchestrator context for recall queries (spec §5, §8.3).

### Changes Required

- **DB:** `memory_chunks` table; migration.
- **Service:** CRUD + “retrieve relevant chunks” (start with **recent + simple keyword/session filter**; upgrade to embedding search only if spec’d later).
- **Orchestrator:** Prompt section for memory; explicit behavior when no memory hit (spec T-4).

### Success Criteria

#### Automated Verification

- [ ] `make test` — store two sessions, ensure isolation; recall test for same session
- [ ] `make lint`

#### Manual Verification

- [ ] End-to-end: prior turn referenced in a later “what did we discuss” style query (voice path)

**Note:** Pause for human confirmation after this phase before proceeding.

---

## Phase 4: Kotlin Android client

### Overview

Ship **single PTT** Compose UI, **EncryptedSharedPreferences** (or DataStore) for device id + session token, **WebSocket** client with **binary/JSON** protocol from Phase 0, **AudioRecord** (or Oboe if needed later) for hold-to-talk, **AudioTrack** or **ExoPlayer** for playback, **interrupt** gesture per assumption table, **audio focus** and Bluetooth basics (spec §9).

### Changes Required

- **App module:** One main screen; states: Idle / Listening / Thinking / Speaking (non-keyboard indicators).
- **Networking:** OkHttp WebSocket or Ktor client; reconnect with backoff.
- **Permissions:** `RECORD_AUDIO`; runtime permission flow.
- **Build config:** Backend base URL via `BuildConfig` (no secrets in repo).

### Success Criteria

#### Automated Verification

- [ ] `make test` — `./gradlew test` (unit tests for protocol framing, state machine)
- [ ] `make lint` — `./gradlew lint` or `ktlint` as configured

#### Manual Verification

- [ ] Hold PTT → speak → release → hear response
- [ ] **Tap PTT during playback** → audio stops, next hold works
- [ ] Airplane mode / server down → non-crashing error state (icon or short spoken error if backend sends TTS error clip)
- [ ] **No required screen** uses `EditText` or keyboard for core flow

**Note:** Pause for human confirmation after this phase before proceeding.

---

## Phase 5: Hardening and evaluation

### Overview

**Rate limits** per device/IP, **observability** (metrics: STT latency, TTS TTFB, tool errors, interrupt count), **evaluation set** for grounding and freshness labeling, **delete session/data** hook if policy requires, documentation for evaluators (hosted vs local).

### Changes Required

- **Gateway:** Throttling + structured rate-limit responses.
- **Ops:** Log aggregation guidance; optional OpenTelemetry.
- **Tests:** Curated eval scenarios (document in `docs/` or `eval/`) covering spec §7 MVP criteria where automatable.
- **README:** How to run backend + Android against staging config; env var list.

### Success Criteria

#### Automated Verification

- [ ] `make test` — full suite green in CI (introduce CI if not present)
- [ ] `make lint` — full workspace

#### Manual Verification

- [ ] Run through PRD-aligned scenario checklist: status Q, GitHub Q, memory, interrupt, self-description, unsupported request
- [ ] Confirm **~3 min** freshness messaging appears when using stale cache fixtures

---

## Technical tradeoffs and alternatives

| Decision | Preferred in this plan | Alternative | When to switch |
|----------|-------------------------|-------------|----------------|
| Client runtime | Kotlin Android (spec) | React Native (research) | Only if product revokes Kotlin requirement |
| Transport | WebSocket + binary audio (spec §6) | HTTP multipart + SSE | If corporate proxies block WSS consistently |
| Backend language | Kotlin Ktor (default assumption) | Python FastAPI | Team expertise or AI SDK fit |
| Memory retrieval | Recent chunks + simple match | Vector DB | If recall quality insufficient in testing |

---

## References

- Technical spec: `docs/specs/1-initial.md`
- PRD: `docs/prd.md`
- Research: `docs/research/2026-03-26-voice-stack-service-selection.md`, `docs/research/2026-03-26-github-integration-contract.md`, `docs/research/2026-03-26-mobile-audio-lifecycle-and-app-model.md`, `docs/research/2026-03-26-grounding-and-answer-composition.md`
- Planning template: `agent/prompts/plan.md`
