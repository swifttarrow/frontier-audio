# Spec 002: Verification and spec alignment

## Summary

Lock acceptance with **tests** and **light documentation** updates so PRD cross-session memory, technical spec §5 / §8.3, and implementation all agree on **device-scoped recall** without session resume.

**Plan:** [Phase 3b](../../1-mvp.md#phase-3b-device-scoped-memory-cross-session-recall)

## Scope

### In scope

- **Tests:** Extend or add `MemoryRepository` / `MemoryService` / pipeline tests (as appropriate) for:
  - same `deviceId`, two sessions → shared retrieval;
  - different `deviceIds` → isolation.
- **`docs/specs/1-initial.md`:** §8.3 step 1 already states **device-scoped** `MemoryChunk` retrieval vs **session-scoped** turns — re-read after code ships and adjust wording only if behavior diverges.
- **`docs/eval/mvp-scenarios.md`:** Add or mark a scenario: disconnect/reconnect (or new `session.start`) → recall prior topic.
- **`docs/milestones/m3-memory/002-memory-service-save-and-retrieve.md`:** Already references “no cross-session reads” for the original m3 slice; ensure a one-line pointer to **m6** exists (see that file’s header note).

### Out of scope

- Full PRD rewrite; Android changes (not required for option A)

## Dependencies

- **Prior:** [001](./001-backend-device-scoped-memory.md) implemented

## Verification

### Automated

- [ ] `make test` green after new cases land

### Manual

- [ ] Evaluator can run the new/updated scenario from `docs/eval/mvp-scenarios.md`
