# Jarvis — Technical Specification

**Derived from:** `docs/prd.md`  
**Template:** `agent/prompts/tech-spec.md`  
**Scope note:** All PRD **Bonus** items are **out of scope** except **mobile on Kotlin** (PRD §7 Bonus #1, §9 B-1). **Passive listening, multi-tenant auth, preference rules, and agentic PR flows are excluded.**  
**Client UX constraint (stakeholder):** Single, uniform, simple **press-to-talk** control; **no manual text entry via the mobile soft keyboard** for any required flow.

---

## 1. Summary

We are building **Jarvis**: a voice-first assistant that answers from a **provided operational API** and **public GitHub** data, with **cross-session memory**, explicit uncertainty when data is missing, and **interruptible** playback. The **primary user surface** is a **Kotlin** mobile app (Android-first; see Assumptions) that exposes **one** full-screen or prominently centered **press-to-talk** affordance—hold to speak, release to end the utterance (or equivalent clearly documented gesture)—and plays **spoken** responses. Audio and conversation state are exchanged with a **backend** that performs speech recognition, orchestrates retrieval and reasoning against GitHub and the API, enforces grounding rules, persists memory, and streams or returns synthesized speech. Configuration that would otherwise require typing (e.g. default GitHub repo URL, API endpoint hints) is supplied **without the mobile keyboard** via **build-time config, remote configuration, or non-keyboard channels** (e.g. deep link, QR) as decided in §11.

---

## 2. Goals and non-goals

### Goals (mapped to PRD)

| Goal | PRD anchor |
|------|------------|
| Voice loop: speak → assistant responds in speech | §4 JTBD, §7 criteria 1, §9 V-1/V-2 |
| Timely “working” feedback for long operations (audible preferred) | §7 criteria 2, §9 V-3 |
| Cross-session memory and recall | §7 criteria 3, §9 V-5 |
| Interrupt/stop and clean recovery for next turn | §7 criteria 4, §9 V-4, §10 |
| Accurate self-description of capabilities | §7 criteria 5, §9 T-3 |
| No fabrication for API/GitHub facts | §7 criteria 6–8, §9 T-1–T-4 |
| GitHub Q&A for **public** URL **as configured** without user typing URL on device | §9 I-1 + stakeholder no-keyboard constraint |
| API-backed Q&A with **~3 min** freshness awareness | §5 Freshness, §9 I-2/I-3 |
| **Kotlin** mobile client with **single PTT button** UI | §7 Bonus mobile, §9 B-1 (only bonus in scope) |

### Non-goals (explicit)

| Non-goal | PRD anchor |
|----------|------------|
| Private GitHub repos | §6 Non-goals |
| Pixel-perfect / rich visual chrome beyond one PTT control + minimal status | §6, §5 Interaction |
| Passive / always-listening background activation | §6, §9 B-2 |
| Sign-in, 10+ concurrent users, per-user isolation, preference rules | §9 B-3 |
| Autonomous “open a PR to fix” flow | §9 B-4 |
| **Swift** client (Kotlin only per stakeholder) | §7 Bonus #1 |
| **Any required flow** that depends on the user **typing** on the mobile keyboard | Stakeholder constraint |

---

## 3. System context

**Actors**

- **End user:** Operates the Kotlin app; uses PTT only for “input.”
- **Jarvis backend:** Session orchestration, STT/LLM/TTS pipeline, tool calls to GitHub and operational API, memory read/write.
- **Operational API:** Source of truth for non-GitHub facts (spec and auth **TBD** per PRD §13).
- **GitHub (public):** REST/GraphQL as appropriate for repo metadata, issues, PRs, comments, merges within rate limits.
- **Optional:** Remote config service, admin or CI that injects repo URL / environment without end-user typing.

**Deployment boundary**

- **Mobile:** Kotlin app on user device (microphone, speaker/earpiece, network).
- **Server:** One or more services behind HTTPS (and optionally WSS) reachable from the device; holds secrets (API keys, GitHub token if needed server-side), memory store, and integration logic.

**Assumption:** GitHub and operational API are called **from the backend** (not from the app) so tokens and rate-limit handling stay server-side; the app sends audio and receives audio/events.

---

## 4. Architecture

### Textual diagram (data flow)

1. User **presses and holds** PTT → app captures PCM (or encodes to Opus/PCM stream) → **upload/stream** to backend.
2. Backend runs **STT** → obtains user text; may emit **“processing”** audio or a short TTS clip early if retrieval is slow (PRD V-3).
3. **Orchestrator** (LLM with tool use or fixed pipeline) calls **GitHub** and/or **operational API** using **server-stored** default repo URL and API base URL; merges **memory** from store.
4. Response text is grounded: no fields invented; missing data → explicit uncertainty (PRD T-1–T-4).
5. **TTS** → audio stream or chunks to client; client plays; **interrupt** stops playback and discards buffered audio (PRD V-4).
6. **Memory writer** persists turn summaries or structured snippets for later recall (PRD V-5).

### Major components

| Component | Responsibility |
|-----------|----------------|
| **Android (Kotlin) app** | Mic permission, PTT gesture, recording lifecycle, network client, audio playback, interrupt handling, minimal non-text status (e.g. listening / thinking / speaking). |
| **API gateway / session service** | Authenticate device or anonymous session, correlate WebSocket/HTTP requests, rate limits. |
| **Voice pipeline service** | STT in, TTS out, streaming if supported by chosen vendors. |
| **Agent / orchestration** | Prompting, tool routing, GitHub + API clients, freshness labeling (~3 min), anti-fabrication patterns (citations from raw JSON, refusal rules). |
| **Memory service** | Store and retrieve conversation history keyed by **session or device identity** (see §7). |
| **Configuration** | Default public `repoUrl`, API base URL, feature flags—**no reliance on user keyboard** on mobile. |

---

## 5. Data model

**Entities (logical)**

- **DeviceSession** — `sessionId` (UUID), `deviceId` or install-bound id, `createdAt`, `lastActiveAt`.
- **ConversationTurn** — `turnId`, `sessionId`, `role` (user|assistant), `text` (after STT or model), `audioArtifactRef` (optional), timestamps; **no raw audio retention** beyond processing window unless policy says otherwise (open with legal).
- **MemoryChunk** — `sessionId` (or user scope if later), `summary` or structured facts, `sourceTurnIds`, `createdAt`; used for “yesterday” / prior topic recall.
- **IntegrationConfig** (server-side) — `defaultPublicRepoUrl`, `operationalApiBaseUrl`, refresh policy metadata, version.

**Keys and identity**

- **Assumption (MVP):** Anonymous **device-scoped** memory (e.g. Android ID + keychain-stored random secret rotated on reinstall) so PRD memory works without bonus sign-in. Document migration path if auth is added later.

**Retention / deletion**

- Align with PRD §13 Q3: define retention for transcripts and memory; support **delete my data** for the device/session if required—**Open question** for legal/product.

**Schema strategy**

- Start with **PostgreSQL** or similar for sessions + memory + config metadata; object storage optional for short-lived audio. **Assumption:** team may substitute managed equivalents; behavior above is the contract.

---

## 6. APIs and interfaces

**Client ↔ backend**

- **Option A (preferred for latency):** **WebSocket** (or WebRTC data channel) for bidirectional streaming: binary audio frames up, control messages (session, interrupt, state) both ways, audio frames down.
- **Option B:** **HTTP** multipart or chunked upload for utterance + SSE or chunked download for TTS.

**Assumption:** WebSocket with JSON control + binary audio unless profiling favors otherwise.

**Representative operations**

- `session.start` — returns `sessionId`, server capabilities, current configured `repoDisplayName` (no secret URLs in logs).
- `audio.frame` / end-of-utterance — triggers STT + pipeline.
- `interrupt` — cancel TTS and reset playback (PRD V-4).
- `session.end` — optional flush.

**Backend ↔ GitHub**

- REST or GraphQL; respect rate limits; cache with TTL **≤** operational freshness expectations where safe; never fabricate fields not in payload.

**Backend ↔ operational API**

- Per shared API spec (PRD §13); include `asOf` or cache timestamp in model context so the LLM can qualify staleness vs **~3 min** policy.

**Error model**

- Structured errors: `code`, `message`, `retryable`; client maps to short spoken summary + optional non-text icon.

**Idempotency**

- Turn processing keyed by `clientTurnId` to avoid duplicate side effects on retry.

**Rate limits**

- Per `deviceId` / IP on voice endpoints to control cost and abuse.

---

## 7. AuthN / AuthZ

- **MVP (no bonus sign-in):** **Device-bound session token** issued on first connect; stored in Android **EncryptedSharedPreferences** or DataStore + optional attestation later.
- **AuthZ:** Single-tenant MVP—all devices use same operational API credentials server-side unless product supplies per-tenant config without keyboard (e.g. different build flavor).
- **Secrets:** GitHub PAT, operational API keys **only on server**.

---

## 8. Key workflows

### 8.1 Press-to-talk turn (happy path)

1. User holds PTT → UI shows “Listening”; app streams audio to backend.
2. User releases → end-of-utterance; STT produces text.
3. If tools need network, backend plays or sends **short “working”** cue (PRD V-3), then fetches GitHub/API + memory.
4. Model produces answer; TTS streams to app; user hears reply.
5. Turn and summary persisted to memory.

### 8.2 Interrupt

1. User taps **same PTT** (or dedicated **Stop**—stakeholder asked for **one** button; **Assumption:** press-while-speaking = interrupt **or** release-then-quick-tap; **confirm in design** so one control still satisfies “single button”) → client sends `interrupt`, stops playback, clears queue.
2. Server cancels generation/TTS where possible; session ready for next PTT.

*Open product detail:* If strictly **one** visible control, define whether **stop** is **long-press**, **double-tap**, or **tap during playback**—must not require keyboard.

### 8.3 Memory recall (“we talked about X yesterday”)

1. Utterance transcribed; orchestrator retrieves **MemoryChunk** for session.
2. Answer uses only retrieved content; if missing, assistant says it does not recall (PRD T-4).

### 8.4 GitHub-grounded question (no user-typed URL)

1. Questions use **server-configured** `defaultPublicRepoUrl` (or small allowlist selected via remote config / deep link).
2. If user asks about a **different** repo and none is configured, assistant explains boundary (PRD §10) **without** asking them to type a URL on phone.

### 8.5 Missing API data

1. Tool returns empty or error; model responds with explicit gap; no invented fields (PRD T-1, §10).

---

## 9. Non-functional requirements

| Area | Requirement |
|------|-------------|
| **Latency** | Target natural turn-taking on good networks; measure STT+first-TTS-byte; align with PRD §12 (directional, not SLA). |
| **Freshness** | Backend labels API-backed answers with cache time; refresh or qualify if older than **~3 minutes** (PRD §5, I-3). |
| **Reliability** | Graceful degradation on GitHub rate limit (spoken + retry later); offline detection with spoken error. |
| **Observability** | Structured logs with `sessionId`, `turnId`, **no PII in clear text** where avoidable; metrics on STT/TTS latency, tool errors, interrupt rate; tracing across services. |
| **Security** | TLS everywhere; encrypt data at rest for memory DB; threat model for anonymous session hijack (token entropy, binding). |
| **Mobile** | Handle audio focus, Bluetooth headsets, brief background (PTT **foreground**—no passive mode); respect Android permissions and data safety form requirements. |

---

## 10. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| **No keyboard** conflicts with PRD wording “user provides public GitHub URL” | Shift to **configured/deeplink/QR** repo; document product acceptance (§11). |
| **Hallucinations** on structured facts | Tool-only answers for numbers/IDs; prompt + evals + tests on golden questions. |
| **Voice latency** | Streaming STT/TTS; early “working” utterance; keep tool payloads small. |
| **GitHub rate limits** | Server-side caching, backoff, user-visible “try again.” |
| **Single-button UX ambiguity** | Prototype interrupt gesture; user-test PTT vs stop. |
| **API spec delay** | Stub contract + versioned adapter in orchestration (PRD §11 Q1). |

---

## 11. Open questions

1. **GitHub URL without keyboard:** Confirm **default repo via build config + remote config** vs **QR/deep link** for changing target (PRD I-1 vs stakeholder constraint).
2. **Interrupt UX** with exactly **one** visible control: long-press vs tap-during-playback vs double-tap—needs product/design sign-off (PRD V-4).
3. **API specification, auth, errors** (PRD §13 Q1).
4. **Retention/privacy** for transcripts and memory (PRD §13 Q3).
5. **Evaluators:** hosted vs local demo (PRD §13 Q2).
6. **“Public GitHub URL” scope:** default branch only vs refs (PRD §13 Q4).
7. **Kotlin scope:** **Android-only** vs **Kotlin Multiplatform** with shared client logic—**Assumption:** Android (Jetpack Compose) unless stakeholders require KMP.

---

## 12. Implementation phases

1. **Phase 0 — Contracts:** Lock operational API adapter shape; define WebSocket protocol; config story for repo URL (no keyboard).
2. **Phase 1 — Backend skeleton:** Session, STT/TTS integration, echo bot, logging.
3. **Phase 2 — Tools:** GitHub read path + operational API read path with grounding tests.
4. **Phase 3 — Memory:** Persistence + recall in orchestrator.
5. **Phase 4 — Kotlin client:** Single PTT screen, streaming, playback, interrupt, minimal states.
6. **Phase 5 — Hardening:** Rate limits, failure modes, observability, evaluation set for no-fabrication and freshness labeling.

---

*This spec intentionally excludes Bonus items B-2–B-4 and all MVP requirements that would **require** mobile keyboard entry; any PRD scenario that implied typed URL on device is superseded for this delivery by configuration or non-keyboard provisioning.*
