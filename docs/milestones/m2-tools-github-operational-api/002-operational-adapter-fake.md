# Spec 002: Operational API adapter — interface + fake implementation

## Summary

Implement the **`OperationalApiAdapter`** interface defined in `docs/contracts/operational-api-adapter.md` with a **FakeOperationalAdapter** returning **golden JSON fixtures** for tests and dev, and wire **dependency injection** so a future **HttpOperationalAdapter** can replace it without changing the orchestrator.

**Plan:** [Phase 2](../../1-mvp.md#phase-2-tools--github-and-operational-api)

## Scope

### In scope

- Interface methods matching contract doc
- `FakeOperationalAdapter` reading from `src/test/resources/fixtures/operational/*.json` and `src/main/resources/...` for dev
- Factory: if `OPERATIONAL_API_BASE_URL` is blank → fake; else → HTTP stub throwing `not_implemented` until spec lands **or** minimal GET if URL is a mock server

### Out of scope

- Full real API until OpenAPI delivered

## Dependencies

- **Prior specs:** [m0/002](../m0-contracts-and-repo-skeleton/002-operational-api-adapter-contract.md)
- **External:** None for fake mode

## Interfaces & contracts

### Public API

- Same as contract doc; ensure `OperationalResult` includes `asOf`

### Data & config

| Env | Purpose |
|-----|---------|
| `OPERATIONAL_API_BASE_URL` | empty = fake |
| `OPERATIONAL_FIXTURE_PROFILE` | optional: `empty`, `error`, `happy` for demos |

## Behavior

### Acceptance criteria

1. Orchestrator can call `healthSummary()` in tests without network.
2. Fixture profile `empty` returns empty collections; orchestrator tests (m2-003) assert no hallucinated fields.
3. `asOf` is **fixed** in fixtures for deterministic evals OR set to `Instant.parse` from JSON—document.

### Edge cases & errors

- `error` profile throws `OperationalApiException` with `retryable: false` for user messaging tests

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../operational/OperationalApiAdapter.kt` | Interface |
| Create | `services/voice-gateway/src/.../operational/FakeOperationalAdapter.kt` | Fixtures |
| Create | `services/voice-gateway/src/test/resources/fixtures/operational/health.json` | Golden data |

## Verification

### Automated

- [ ] Unit tests for each fixture profile

### Manual

- [ ] Toggle env; voice response reflects fixture content

## Notes

- When real API arrives, add **contract tests** against recorded responses in m5.
