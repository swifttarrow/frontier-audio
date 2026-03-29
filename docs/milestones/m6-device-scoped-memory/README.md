# Milestone 6: Device-scoped memory (cross-session recall, option A)

## Overview

**Option A:** Each WebSocket connect still creates a **new** `device_sessions` row (no `sessionToken` resume). **Recall** uses **device identity**: load recent `memory_chunks` for the same `deviceId` across all past sessions, inject into the orchestrator so questions like “yesterday we talked about X” work per `docs/specs/1-initial.md` §5 and PRD memory.

**Plan reference:** [Phase 3b: Device-scoped memory](../../1-mvp.md#phase-3b-device-scoped-memory-cross-session-recall)

**Why a follow-up milestone:** [m3](../m3-memory/) implemented persistence and **same-`sessionId`** retrieval only (`docs/milestones/m3-memory/002-memory-service-save-and-retrieve.md` explicitly scoped out cross-session reads). This milestone closes the gap without session resume (no option B).

## Dependencies

- [x] Milestone 3 — memory chunks, `SimpleMemoryService`, `TurnPipeline` injection
- [x] Milestone 1 — `device_sessions.device_id`, `memory_chunks.session_id` FK

## Changes Required (task list)

- [ ] **Repository:** Query recent chunks by **`deviceId`** (join `memory_chunks` → `device_sessions`, filter `device_sessions.device_id`, order by `memory_chunks.created_at`, limit N).
- [ ] **Service API:** Expose retrieval by device (e.g. `recentChunksForDevice(deviceId, limit)`); keep **writes** keyed by current `sessionId` (FK unchanged) or document if denormalizing `device_id` on chunks later.
- [ ] **TurnPipeline:** Pass `session.deviceId` into memory retrieval; keep `recentTurns(sessionId)` for **in-session** dialogue only.
- [ ] **Isolation:** Queries must never return chunks for another `deviceId` (tests).
- [ ] **Docs:** Align `docs/specs/1-initial.md` §8.3 wording with device-scoped recall; add/adjust eval scenario for reconnect / new session.
- [ ] **Optional:** Composite DB index `(device_id via join)` or denormalized `device_id` on `memory_chunks` if explain shows slow scans at scale.

## Success Criteria

### Automated Verification

- [ ] `make test` — same `deviceId`, **two** distinct `sessionId`s: chunks written under session 1 appear in retrieval for session 2; different `deviceId` still isolated.
- [ ] `make lint`

### Manual Verification

- [ ] Voice or WS test client: converse → disconnect → reconnect (new session) → recall prior topic from memory context.

## Implementation specs

- [001-backend-device-scoped-memory](./001-backend-device-scoped-memory.md)
- [002-verification-and-spec-alignment](./002-verification-and-spec-alignment.md)
