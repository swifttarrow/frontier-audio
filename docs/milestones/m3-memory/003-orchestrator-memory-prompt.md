# Spec 003: Orchestrator memory injection and recall behavior

## Summary

Extend **`VoiceOrchestrator`** to load **`recentChunks`** for the `sessionId` on each user utterance, add a **Memory** section to the system or user context, and enforce **T-4**: if user asks about past discussion and **no chunk** matches, assistant **must** say it does not recall—not invent prior content.

**Plan:** [Phase 3](../../1-mvp.md#phase-3-memory) · **Spec:** `docs/specs/1-initial.md` §8.3

## Scope

### In scope

- Prompt block: “Prior session summaries (may be incomplete): …” listing summaries with timestamps
- After assistant response finalized, call `MemoryService.appendTurnSummary`
- Golden tests for: (a) chunk contains “budget” from prior turn, user asks “what did we say about budget?” → answer references it; (b) no chunk → safe denial

### Out of scope

- “Yesterday” absolute time resolution beyond what timestamps in summaries provide

## Dependencies

- **Prior specs:** [m3/002](./002-memory-service-save-and-retrieve.md), [m2/003](../m2-tools-github-operational-api/003-llm-orchestrator-tool-calling.md)
- **External:** None

## Interfaces & contracts

### Public API

- Orchestrator constructor gains `MemoryService`
- Internal: `buildMemoryContext(sessionId: UUID): String`

## Behavior

### Acceptance criteria

1. Memory context never includes **other** `sessionId` data (test with two parallel sessions).
2. When `recentChunks` empty and user asks recall question, output contains explicit **don’t recall** phrasing (snapshot test).
3. Memory injection **does not** replace tool requirement for GitHub/API facts.

### Edge cases & errors

- Token overflow: keep last N chunks only, drop oldest first

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Modify | `services/voice-gateway/.../VoiceOrchestrator.kt` | Load memory, append summary |
| Modify | `services/voice-gateway/.../agent/SystemPrompt.md` | Memory rules |

## Verification

### Automated

- [ ] Golden recall + golden no-recall tests

### Manual

- [ ] Two-turn voice conversation demonstrates recall

## Notes

- Add eval case to m5 checklist for “yesterday” wording if timestamps support it.
