# Spec 004: Tool error envelopes and user-facing messaging

## Summary

Normalize **GitHub**, **operational**, and **LLM** failures into the **WebSocket error** shape (`code`, `message`, `retryable`) and/or **spoken** assistant messages that **do not** invent facts. Ensures `docs/specs/1-initial.md` §6 error model is consistently applied end-to-end.

**Plan:** [Phase 2](../../1-mvp.md#phase-2-tools--github-and-operational-api)

## Scope

### In scope

- Central `mapThrowableToUserFacing(t: Throwable): UserFacingError`
- Policy table: which errors trigger **WS `error` frame** vs **TTS apology** vs **both**
- Rate limit: spoken text includes “try again in a moment” + `retryable: true`
- Logging: always log full exception at DEBUG; user message sanitized

### Out of scope

- Localization beyond English for MVP

## Dependencies

- **Prior specs:** [m2/001](./001-github-client-caching-ratelimit.md), [m2/003](./003-llm-orchestrator-tool-calling.md)
- **External:** None

## Interfaces & contracts

### Data types

```text
data class UserFacingError(
  val code: String,
  val message: String,
  val retryable: Boolean,
  val speak: String // text for TTS
)
```

### Public API

- `fun toWsErrorPayload(e: UserFacingError): JsonObject`
- `fun toAssistantSpeak(e: UserFacingError): String` (may equal `message` or shorter)

## Behavior

### Acceptance criteria

1. `GitHubApiException(rate_limited)` maps to `retryable=true` and speak string ≤ **120 chars**.
2. Unknown `Throwable` maps to `code=internal.error`, `retryable=false`, speak: “Something went wrong.”
3. **Never** include PAT fragments or stack traces in `speak` or WS `message`.

### Edge cases & errors

- Concurrent errors on same turn: first error wins; turn marked failed

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../errors/UserFacingErrorMapper.kt` | Mapping |
| Modify | `services/voice-gateway/.../VoiceOrchestrator.kt` | Use mapper |

## Verification

### Automated

- [ ] Table-driven tests for each exception type

### Manual

- [ ] Force 429 in staging; client receives expected UX

## Notes

- Align codes with future metrics dashboards (m5).
