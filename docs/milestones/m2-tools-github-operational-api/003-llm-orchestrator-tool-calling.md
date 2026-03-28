# Spec 003: LLM orchestrator — tool calling and grounding prompts

## Summary

Replace the m1 **echo** reply path with an **LLM** step that **must** use **tools** (`github_*`, `operational_*`) to answer factual questions about repo or API data. System prompt enforces: **no fabricated** issue numbers, counts, or API fields; cite or paraphrase **only** tool JSON; include **`asOf`** when relevant for freshness (~3 min). Spoken output is still delivered via existing **TTS** path.

**Plan:** [Phase 2](../../1-mvp.md#phase-2-tools--github-and-operational-api) · **Research:** `docs/research/2026-03-26-grounding-and-answer-composition.md`

## Scope

### In scope

- Tool definitions (JSON schema or vendor format) for: GitHub read ops exposed in m2-001, operational ops in m2-002
- Orchestration loop: user transcript → model → tool calls → second model pass → final assistant text → TTS
- **Working cue:** if first tool call not completed within **N** ms (e.g. 800ms), emit short TTS “One moment.” or play cached clip (PRD V-3) — implement at least **logging** + optional TTS hook
- **Self-description:** when user asks capabilities, respond from **static** allowed capability list aligned with `docs/specs/1-initial.md` §2 (no claiming private repo, etc.)

### Out of scope

- Memory injection (m3)
- Multi-turn tool plans beyond reasonable token budget guard

## Dependencies

- **Prior specs:** [m2/001](./001-github-client-caching-ratelimit.md), [m2/002](./002-operational-adapter-fake.md), [m1/004](../m1-backend-skeleton/004-audio-pipeline-stt-tts-echo.md)
- **External:** LLM API key; model names via env

## Interfaces & contracts

### Functions/modules

```text
interface VoiceOrchestrator {
  suspend fun handleUserUtterance(
    sessionId: UUID,
    clientTurnId: UUID,
    transcript: String
  ): Flow<AssistantEvent> // text chunks + tts control, or single final — document
}
```

- `AssistantEvent` variants: `TextDelta`, `TtsAudio`, `Error` — align with WS downlink types from m0

### Data & config

| Env | Purpose |
|-----|---------|
| `LLM_MODEL` | reasoning model |
| `TOOL_TIMEOUT_MS` | per tool call |

## Behavior

### Acceptance criteria

1. **Golden test:** Given tool payload with 2 open PRs, model output mentions **exactly** those titles/numbers; given empty list, model says there are none (no invented PR).
2. Tool **timeout** → assistant explains failure without fabricating data.
3. Operational answer includes **when** data was fetched if `asOf` present in `OperationalResult`.
4. `interrupt` cancels in-flight LLM and TTS (best-effort; same as m1-004 extended).

### Edge cases & errors

- Model requests unknown tool → server returns tool error to model or short-circuits with safe message

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../agent/VoiceOrchestrator.kt` | Loop |
| Create | `services/voice-gateway/src/.../agent/Tools.kt` | Tool registrations |
| Create | `services/voice-gateway/src/.../agent/SystemPrompt.md` | Versioned prompt |
| Modify | `services/voice-gateway/.../TurnPipeline.kt` | Call orchestrator instead of echo |

## Verification

### Automated

- [ ] Golden tests with **frozen** tool outputs and snapshot of assistant text
- [ ] Empty GitHub list test

### Manual

- [ ] Ask “what are open PRs?” against known repo; compare to GitHub UI

## Notes

- Prefer **structured** tool outputs (JSON) passed verbatim in assistant context for auditability.
