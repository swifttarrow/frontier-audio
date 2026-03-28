# Spec 002: Operational API adapter contract

## Summary

Document the **backend-only** adapter that supplies operational data to the orchestrator. The real HTTP API is TBD; this contract defines the **Kotlin (or Python) interface**, error mapping, and **freshness** metadata (`asOf`) so m2 can ship a **fake** implementation and swap in HTTP later without changing the orchestrator.

**Plan:** [Phase 0](../../1-mvp.md#phase-0-contracts-and-repository-skeleton) · **Spec:** `docs/specs/1-initial.md` §6 backend ↔ operational API

## Scope

### In scope

- Adapter **capabilities** as high-level read operations (e.g. `fetchStatusSnapshot()`, `fetchIncidents()` — **name and document** the minimal set needed for MVP demos; align with stub fixtures)
- **Request context**: `OperationalQuery` object with optional filters (strings only in v1)
- **Response** wrapper: `data` (JSON-serializable object or typed DTO list), `asOf` (ISO-8601 instant when data was obtained from upstream or cache), `cacheTtlSeconds` optional
- **Errors**: `OperationalApiException` or equivalent with `code`, `message`, `retryable`, `httpStatus` optional
- Mapping rules to WebSocket/user-visible errors (reference §6 error model)
- Fake/stub behavior contract: deterministic fixtures for tests (golden JSON files under `services/voice-gateway/src/test/resources/` or equivalent)

### Out of scope

- Real HTTP paths and auth headers until API spec lands
- Write operations

## Dependencies

- **Prior specs:** None (orthogonal to WS framing)
- **External:** None

## Interfaces & contracts

### Public API — adapter interface (language-agnostic description)

**Operations** (adjust names to match eventual API spec; minimum for MVP):

| Operation | Input | Output | Notes |
|-----------|--------|--------|--------|
| `healthSummary` | none | `OperationalResult<Map<String, Any>>` | Stub returns fixture `fixtures/operational/health.json` |
| `alertsOrEvents` | optional `limit: Int` | `OperationalResult<List<Map<String, Any>>>` | Empty list valid |

**`OperationalResult<T>`**

```text
data class OperationalResult<T>(
  val data: T,
  val asOf: Instant,
  val source: String // e.g. "cache" | "live"
)
```

(Use equivalent record in Python if FastAPI.)

**Thrown/rejected errors**

| Code | retryable | When |
|------|-----------|------|
| `operational.unavailable` | true | upstream timeout |
| `operational.not_found` | false | resource missing |
| `operational.forbidden` | false | auth failure |

### Data & config

- Env: `OPERATIONAL_API_BASE_URL` optional; when unset, **FakeOperationalAdapter** is used in dev profile

## Behavior

### Acceptance criteria

1. `docs/contracts/operational-api-adapter.md` lists every operation, types, and error codes.
2. Orchestrator prompt instructions can cite: “operational facts MUST come only from `OperationalResult` payloads; include `asOf` when answering.”
3. Fake adapter returns **empty** and **error** modes via env or test hook for m2 golden tests.

### Edge cases & errors

- Empty `data` → orchestrator must not invent fields (tested in m2)
- Stale cache > 3 minutes → still return `asOf`; orchestrator qualifies answer text (m2)

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `docs/contracts/operational-api-adapter.md` | Adapter contract + error codes |

## Verification

### Automated

- [ ] N/A until interface exists in code (m2); this spec gates m2-002

### Manual

- [ ] Reviewer confirms stub can be implemented without guessing return shapes

## Notes

- Keep DTOs **JSON-shaped** early to minimize translation when the real OpenAPI arrives.
