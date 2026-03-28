# Jarvis — Product Requirements Document

**One-line description:** A real-time voice assistant that answers from live GitHub and API data, remembers past conversations, and refuses to invent facts.

---

## 1. Title

| Field | Value |
|--------|--------|
| **Name** | Jarvis |
| **Summary** | Voice-first assistant for accurate, low-latency operational Q&A with persistent memory and external data sources. |

---

## 2. Problem Statement

**What is broken today**  
Frontline workers need cross-team information that is **correct** and **immediate**. Today, generic assistants are optimized for screens and tolerate slow or vague replies; in high-stakes settings, long silence, wrong numbers, and made-up repo or API details erode trust and slow decisions.

**Why it matters**  
When decisions happen in seconds, latency and inaccuracy have outsized cost. A voice assistant that feels natural, signals when it is working, and only speaks from verifiable sources directly supports that operational reality.

---

## 3. Target Users

- **Primary:** People in fast-moving operational roles who need hands-free or eyes-up access to status, repo activity, and system data.
- **Context:** Noisy or mobile environments, short utterances, interruption-prone sessions, and repeat use across days—not one-off demos.

---

## 4. Core Job-To-Be-Done

> *While I am working, I want to ask Jarvis questions by voice and get **fast**, **trustworthy** answers grounded in **our API** and **public GitHub**, with **memory across sessions**, so I can act without hunting tabs or guessing.*

---

## 5. Constraints

| Area | Constraint |
|------|------------|
| **Interaction** | Voice is primary; visual UI is secondary. Experience quality and conversational flow matter more than screen design. |
| **Data sources** | Answers about GitHub must be tied to **public** repository URLs the user (or flow) provides. Answers about operations must come **only** from the **provided API** (spec supplied separately). |
| **Freshness** | API-backed answers must reflect data that is no older than the product’s stated refresh policy (**source data refreshes every 3 minutes**). |
| **Accuracy** | No presenting invented API fields, PR/issue identifiers, or repo facts; uncertainty must be explicit. |
| **Implementation** | Teams may use any languages and frameworks effective for the job (e.g., TypeScript, Python, Kotlin, Go). |

---

## 6. Non-Goals

**MVP non-goals**

- Pixel-perfect or feature-rich visual product chrome.
- Supporting private GitHub repositories unless explicitly moved to bonus scope.
- Guaranteeing sub-millisecond latency or formal SLAs beyond stated **expectations** (see §12).
- Prescribing a single architecture, vendor, or storage technology.

**Bonus is not required for MVP**

- Native mobile clients, always-listening passive mode, multi-tenant scale, sign-in, per-user preference rules, and autonomous “open a PR to fix an issue” flows are **out of MVP scope** unless delivered as optional enhancements.

---

## 7. Success Criteria

Criteria below are **testable** from user-visible behavior. **MVP** items are required for a complete minimum product; **Bonus** items apply only if that scope is claimed.

### MVP — required outcomes

1. **Voice-first loop:** User can complete a turn (speak → assistant responds with speech or an agreed voice-accessible channel) without relying on a heavy UI workflow.
2. **Low dead air:** User receives timely feedback when the system is fetching or thinking, so long unexplained silence is avoided; audible notification is preferred when work takes noticeable time.
3. **Cross-session memory:** Assistant can recall prior sessions’ Q&A when asked (e.g., references to “yesterday” or earlier topics).
4. **Interruptibility:** User can interrupt or stop the assistant (e.g., explicit stop phrase); system recovers cleanly for the next turn.
5. **Self-description:** When asked what it can do, assistant describes its real capabilities and limits accurately (API + public GitHub, no false features).
6. **No fabrication:** For API and GitHub factual questions, assistant does not invent data; it acknowledges gaps or retrieves before asserting.
7. **GitHub coverage:** Given a public GitHub URL, assistant can answer questions about the repository, including (as supported by live data) open PRs, issues, PR comments, recent merges, and related metadata.
8. **API grounding:** Assistant answers API-related questions using only information from the provided API, consistent with the 3-minute refresh reality.

### Bonus — optional outcomes (if in scope)

1. **Mobile:** Core experience available on mobile via **Kotlin** or **Swift** (as specified in source requirements).
2. **Passive mode:** App can run in the background and activate on voice when spoken to, with acceptable false-trigger and privacy tradeoffs documented.
3. **Scale & personalization:** System supports **10+ simultaneous users**, authenticated identities, **isolated** per-user data, and **user preference rules** (e.g., never mention X; always flag Y) applied consistently.
4. **End-to-end agent:** User can request analysis of an issue and **opening of a new PR** that attempts a fix; a real PR exists as a result of that flow.

---

## 8. Key User Scenarios

**MVP**

- User asks a status question; assistant answers from the **latest API data** available under the refresh policy.
- User provides a **public GitHub repo URL** and asks about open PRs or issues; answers match what the repository actually shows.
- User says they discussed “X” yesterday; assistant **recalls** that thread from stored history.
- User cuts off a long reply; assistant **stops** and listens for the next command.
- User asks “What can you do?”; reply matches **actual** integrations and boundaries.
- User asks for something not in the API or repo; assistant says it **doesn’t know** or offers to fetch, without filling in blanks.

**Bonus (if built)**

- Same user stories on a **native mobile** client.
- User enables **background** listening; assistant only engages after an agreed wake pattern.
- Two signed-in users each have **private** memory and preferences; one cannot see the other’s history.
- User asks to **analyze an issue and open a PR**; a new PR appears on the repo with a traceable outcome.

---

## 9. Functional Requirements

Requirements are **behavioral** and **implementation-agnostic**. Unless labeled **Bonus**, they belong to **MVP**.

### MVP — Voice & conversation

| ID | Requirement |
|----|-------------|
| V-1 | The system accepts voice input as the primary way to drive Q&A. |
| V-2 | The system produces spoken (or voice-suitable) responses so the user is not required to read long text for core flows. |
| V-3 | When backend work or retrieval is likely to exceed a short perceptual window, the user is informed that work is in progress (audible feedback preferred). |
| V-4 | The user can interrupt ongoing assistant output and issue a new command without requiring a full session restart. |
| V-5 | Conversation content needed for continuity is **persisted** and available in **later** sessions, not only the current sitting. |

### MVP — Trust, accuracy, and self-knowledge

| ID | Requirement |
|----|-------------|
| T-1 | Factual claims about the **provided API** must be derivable from API responses (or explicit “unknown” / error). |
| T-2 | Factual claims about **GitHub** must be derivable from data retrieved for the given public URL (or explicit “unknown” / error). |
| T-3 | The assistant must state its limitations and real capabilities when asked, without advertising features that are not implemented. |
| T-4 | The system must not substitute plausible but unverified details when data is missing. |

### MVP — Integrations

| ID | Requirement |
|----|-------------|
| I-1 | The user (or configured flow) can supply a **public GitHub repository URL** and ask questions about that repository’s activity and metadata. |
| I-2 | The system can answer questions using **only** the **provided API** as the source of truth for operational data. |
| I-3 | The product’s handling of API data must account for **periodic refresh** (source updates on the order of **every three minutes**); stale answers relative to that policy must be avoided or clearly qualified. |

### Bonus — optional capabilities

| ID | Requirement |
|----|-------------|
| B-1 | **Mobile:** Deliver the assistant experience using **Kotlin or Swift** as the primary client surface (per stakeholder spec). |
| B-2 | **Passive mode:** Allow silent background operation and activation when the user speaks, within platform constraints. |
| B-3 | **Scale & personalization:** Support **at least ten** concurrent users, **sign-in**, **per-user data isolation**, and **preference rules** that constrain answers (e.g., forbidden topics, mandatory flags). |
| B-4 | **Agentic PR workflow:** From a user request, the system can progress from issue inspection to **creating a new pull request** that attempts to address the issue. |

---

## 10. System Behavior

| Situation | Expected behavior |
|-----------|-------------------|
| **Valid request** with available data | Direct, concise answer grounded in API or GitHub data (and memory when relevant). |
| **Valid request** but data not yet fetched | Brief “working” signal, then answer or clear failure—no silent stall. |
| **Missing or empty data** | Explicit acknowledgment; no invented fields, counts, or titles. |
| **Unsupported request** (e.g., private repo on MVP) | Clear boundary: what is not supported and what is. |
| **Ambiguous input** | Clarifying question or safest narrow interpretation, without fabricating repo/API facts. |
| **User interrupt** | Stop current output path; accept next input without repeating obsolete audio unless user asks. |

---

## 11. Risks & Unknowns

- **API spec timing:** Full behavior for API-backed Q&A depends on the **shared API specification**; edge cases and auth model may shift acceptance tests.
- **Voice + latency:** Achieving natural cadence alongside network and model latency may require tradeoffs between streaming, chunking, and user feedback—exact approach is left to the team.
- **GitHub rate limits and volume:** Large repos or burst traffic may affect responsiveness; needs explicit handling strategy (without mandating a specific technology).
- **Hallucination pressure:** Models tend to complete patterns; enforcing “no fabrication” may require process and testing discipline beyond prompt text alone.
- **Bonus scope:** Passive listening and multi-tenant isolation introduce **privacy, security, and compliance** questions that MVP may not need to resolve.

---

## 12. Performance Expectations

These are **directional targets**, not contractual SLAs. Teams should measure and document what they achieve.

| Dimension | MVP expectation (order of magnitude) |
|-----------|-------------------------------------|
| **Perceived voice round-trip** | Fast enough for natural back-and-forth in good network conditions; long operations should be masked with explicit feedback. |
| **API freshness** | User-visible answers should align with the **~3-minute** refresh cadence of the source data; document any client caching policy. |
| **GitHub queries** | Interactive questions should complete within a tolerable wait for a human operator, with progress signaling on slow paths. |
| **Memory recall** | Recalling prior-session snippets should feel immediate relative to network-backed repo/API calls. |

**Bonus (if claimed):** Concurrent use (**10+** users), background activation responsiveness, and end-to-end PR creation time should be documented with honest limits.

---

## 13. Open Questions

1. Exact **API** surface, authentication, and error semantics (pending shared spec).
2. Whether **evaluators** require a hosted deployment, local-only demo, or both.
3. **Retention and privacy** policy for stored voice transcripts and memory (jurisdiction, minimization, deletion).
4. **Minimum bar** for “public GitHub URL” (e.g., default branch only vs arbitrary refs).
5. For **Bonus** agent flows: required human approval gates, branch naming, and org policies for automated PRs.

---

*Source: `docs/requirements.md`. MVP and Bonus labels match the stakeholder split: MVP is required minimum scope; Bonus is optional for extra credit.*
