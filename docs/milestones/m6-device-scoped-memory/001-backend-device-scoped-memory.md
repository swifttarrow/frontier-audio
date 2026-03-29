# Spec 001: Backend — device-scoped memory retrieval

## Summary

Implement **read path** for memory summaries keyed by **`deviceId`**, while **writes** remain tied to the current **`sessionId`** (existing FK). **Option A:** no protocol change on `session.start`; client continues to send `deviceId` only.

**Plan:** [Phase 3b](../../1-mvp.md#phase-3b-device-scoped-memory-cross-session-recall) · **Spec:** `docs/specs/1-initial.md` §5 (device-scoped memory), §8.3

## Scope

### In scope

- `MemoryRepository` (or equivalent): `recentChunksForDevice(deviceId: String, limit: Int)` using a join:

  `memory_chunks` INNER JOIN `device_sessions` ON `memory_chunks.session_id` = `device_sessions.id` WHERE `device_sessions.device_id` = ?

  Order by `memory_chunks.created_at` DESC, limit, then **reverse** to oldest-first for LLM context (match current `recentChunks` behavior).

- `MemoryService`: delegate retrieval for orchestrator context to **device-scoped** method; keep `appendTurnSummary(sessionId, …)` as today.

- `TurnPipeline`: when building `memoryCtx`, call device-scoped retrieval with `session.deviceId` instead of `session.sessionId`.

### Out of scope

- `sessionToken` resume, merging server sessions, or client payload changes
- Semantic / embedding search
- Denormalized `device_id` on `memory_chunks` **unless** profiling requires it (then document in migration note)

## Dependencies

- **Prior:** [m3/001](../m3-memory/001-memory-chunks-schema.md), [m3/002](../m3-memory/002-memory-service-save-and-retrieve.md)

## Behavior

### Acceptance criteria

1. After turns in **session S1** for **device D**, a **new** session **S2** for the same **D** receives non-empty memory context that includes chunks from **S1** (up to `limit` global recency across device).
2. Device **D2** never sees chunks written under **D1**.
3. Write path unchanged: each new chunk row still references the **current** `session_id` for provenance and CASCADE delete behavior.

### Edge cases & errors

- **Empty device history:** Same as today — null/empty memory context; model says it does not recall (T-4).
- **Very chatty devices:** `limit` caps prompt size; optional follow-up: time-window filter in m5 retention work.
- **DB unavailable:** Existing error handling on repository; turn must not fail solely due to memory read failure (match current write behavior philosophy where practical).

## File map (expected)

| Area | Path (voice-gateway) |
|------|----------------------|
| Repository | `.../memory/MemoryRepository.kt` |
| Service | `.../memory/MemoryService.kt` |
| Pipeline | `.../audio/TurnPipeline.kt` |

## Verification

### Automated

- [ ] Integration-style test: two `createSession(deviceId, hash)` with **same** `deviceId`, different `sessionId`; append chunks to session A; assert `recentChunksForDevice(deviceId)` includes them when “acting as” session B (direct repository/service test sufficient).

### Manual

- [ ] See milestone [README](./README.md) manual criteria.
