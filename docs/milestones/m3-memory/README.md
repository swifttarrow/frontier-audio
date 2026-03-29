# Milestone 3: Memory

## Overview

Implement **MemoryChunk** persistence and retrieval keyed by **`sessionId`** for the m3 slice, write summaries after turns, and inject memory into the **orchestrator** for same-session recall and T-4 (no recall → explicit uncertainty). **Cross-session recall (same device, new `session.start`)** is tracked in [m6: Device-scoped memory](../m6-device-scoped-memory/) (option A).

**Plan reference:** [Phase 3: Memory](../../1-mvp.md#phase-3-memory)

## Dependencies

- [ ] Milestone 2 — LLM orchestrator + tools

## Changes Required

- DB table `memory_chunks`, migration
- Service: save chunk after each assistant turn (or batch); retrieve recent chunks for **`sessionId`** (cross-**session** recall on same device → [m6](../m6-device-scoped-memory/))
- Orchestrator prompt section + “no memory hit” behavior

## Success Criteria

### Automated Verification

- [x] `make test` — two sessions isolated; same-session recall test
- [x] `make lint`

### Manual Verification

- [ ] Voice path: prior topic referenced in later question

## Implementation specs

- [001-memory-chunks-schema](./001-memory-chunks-schema.md)
- [002-memory-service-save-and-retrieve](./002-memory-service-save-and-retrieve.md)
- [003-orchestrator-memory-prompt](./003-orchestrator-memory-prompt.md)

## Follow-up (cross-session)

- [m6-device-scoped-memory](../m6-device-scoped-memory/) — Phase 3b / option A in `docs/plans/1-mvp.md`
