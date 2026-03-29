# Jarvis MVP Evaluation Scenarios

**Spec:** `docs/specs/1-initial.md` | **PRD:** `docs/prd.md`

## Voice Loop (V-series)

### V-1: Basic voice round-trip
- **Given** the app is connected and idle
- **When** user holds PTT, says "Hello Jarvis", and releases
- **Then** Jarvis responds with a spoken greeting
- **Ref:** PRD V-1, V-2

### V-2: Working feedback for slow operations
- **Given** a question that requires tool calls (e.g., GitHub fetch)
- **When** user asks and tool call takes > 1s
- **Then** user sees "Thinking" state; response arrives after tools complete
- **Ref:** PRD V-3

### V-3: Interrupt during playback
- **Given** Jarvis is speaking a long response
- **When** user taps PTT button during playback
- **Then** playback stops immediately; next hold-to-talk works normally
- **Ref:** PRD V-4, spec §8.2

### V-4: Cross-session memory recall
- **Given** on the **same device / install**, user discussed "project budget" in an **earlier WebSocket session** (e.g. prior app session: force-stop, relaunch, or reconnect so `session.start` runs again)
- **When** user asks "what did we talk about yesterday?" or "remind me what we discussed about the budget"
- **Then** Jarvis references the budget topic from **device-scoped** memory (not only the current connection’s turns)
- **Ref:** PRD V-5, spec §8.3 · **Milestone:** [m6-device-scoped-memory](../milestones/m6-device-scoped-memory/)

### V-5: No recall when memory is empty
- **Given** fresh session with no prior turns
- **When** user asks "what did we discuss yesterday?"
- **Then** Jarvis says it doesn't recall any prior conversations
- **Ref:** spec T-4

## Truthfulness (T-series)

### T-1: Grounded GitHub answer
- **Given** the configured repo has 2 open PRs
- **When** user asks "how many open pull requests are there?"
- **Then** Jarvis says exactly 2 (or the correct count), naming them
- **Ref:** PRD T-1

### T-2: Empty GitHub data
- **Given** the configured repo has no open issues
- **When** user asks "are there any open issues?"
- **Then** Jarvis says there are no open issues (no fabrication)
- **Ref:** PRD T-2

### T-3: Self-description accuracy
- **Given** any state
- **When** user asks "what can you do?"
- **Then** Jarvis describes: voice Q&A, GitHub queries, operational data, memory — and does NOT claim private repo access, PR creation, or background listening
- **Ref:** PRD T-3, spec §2

### T-4: Freshness qualification (~3 min)
- **Given** operational data was fetched 5+ minutes ago (stale cache)
- **When** user asks "what is the system health?"
- **Then** Jarvis qualifies the answer with timing, e.g., "As of 5 minutes ago..."
- **Ref:** spec §5, §9 Freshness

## Integration (I-series)

### I-1: GitHub query without keyboard
- **Given** default repo is configured server-side
- **When** user asks about PRs by voice only
- **Then** answer is provided without any text input on the device
- **Ref:** PRD I-1, spec §11 Q1

### I-2: Operational health query
- **Given** operational API adapter is configured (fake or real)
- **When** user asks "what is the operational status?"
- **Then** Jarvis reports health data from the adapter with asOf timestamp
- **Ref:** PRD I-2

### I-3: GitHub rate limit handling
- **Given** GitHub API returns 429 or 403 (rate limit)
- **When** user asks a GitHub question
- **Then** Jarvis explains the rate limit and suggests trying again later
- **Ref:** spec §9 Reliability

### I-4: Unsupported request
- **Given** any state
- **When** user asks "open a PR to fix the bug"
- **Then** Jarvis explains it cannot create PRs (non-goal)
- **Ref:** spec §2 Non-goals

## Error handling

### E-1: Airplane mode / no network
- **Given** device has no network connectivity
- **When** user tries to use the app
- **Then** app shows error state without crashing
- **Ref:** spec §9 Reliability

### E-2: No keyboard required
- **Given** the app is running
- **When** evaluator inspects the main flow
- **Then** no `EditText`, keyboard, or text input is required for any core operation
- **Ref:** spec §2, stakeholder constraint
