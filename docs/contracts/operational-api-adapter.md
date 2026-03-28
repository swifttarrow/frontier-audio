# Operational API Adapter Contract

**Purpose:** Define the backend-only adapter interface that supplies operational data to the orchestrator. The real HTTP API is TBD; this contract defines the interface, error mapping, and freshness metadata so a **fake implementation** can ship in m2 and be swapped for real HTTP later without changing the orchestrator.

**Spec reference:** `docs/specs/1-initial.md` section 6 (backend <-> operational API)

---

## Adapter interface

The adapter is a backend-internal abstraction. It is **not** exposed to the client.

### Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `healthSummary` | `() -> OperationalResult<Map<String, Any>>` | Returns a snapshot of system health / status. Stub returns fixture `fixtures/operational/health.json`. |
| `alertsOrEvents` | `(limit: Int?) -> OperationalResult<List<Map<String, Any>>>` | Returns recent alerts or events. Empty list is a valid response. Stub returns fixture `fixtures/operational/alerts.json`. |

### `OperationalResult<T>`

```kotlin
data class OperationalResult<T>(
    val data: T,
    val asOf: Instant,      // when data was obtained from upstream or cache
    val source: String       // "cache" | "live" | "fake"
)
```

Python equivalent:

```python
@dataclass
class OperationalResult(Generic[T]):
    data: T
    as_of: datetime          # timezone-aware UTC
    source: str              # "cache" | "live" | "fake"
```

The orchestrator MUST include `asOf` when composing answers from operational data, so the LLM can qualify staleness against the ~3 minute freshness policy.

---

## Error model

Errors are thrown/raised as `OperationalApiException` (or equivalent).

```kotlin
class OperationalApiException(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val httpStatus: Int? = null   // from upstream, if available
) : RuntimeException(message)
```

### Error codes

| Code | Retryable | When |
|------|-----------|------|
| `operational.unavailable` | true | Upstream timeout or connection failure |
| `operational.not_found` | false | Requested resource does not exist |
| `operational.forbidden` | false | Authentication / authorization failure |
| `operational.bad_request` | false | Malformed query parameters |

### Mapping to WebSocket errors

When the orchestrator catches an `OperationalApiException`, it maps to the WebSocket error envelope:

| Adapter error | WS error code | User-facing guidance |
|---------------|---------------|----------------------|
| `operational.unavailable` | `orchestrator.failed` (retryable) | "Operational data is temporarily unavailable. Try again in a moment." |
| `operational.not_found` | N/A — orchestrator says "no data found" | Explicit gap in response, no fabrication |
| `operational.forbidden` | `orchestrator.failed` (not retryable) | "Unable to access operational data. Contact your administrator." |

---

## Configuration

| Environment variable | Purpose | Default |
|----------------------|---------|---------|
| `OPERATIONAL_API_BASE_URL` | Base URL for the real operational API | unset |

When `OPERATIONAL_API_BASE_URL` is **not set**, the service MUST use `FakeOperationalAdapter` which returns deterministic fixture data.

---

## Fake adapter contract

The `FakeOperationalAdapter` is used for local development and testing.

### Behavior modes

Controlled by environment variable `OPERATIONAL_FAKE_MODE` (default: `normal`):

| Mode | Behavior |
|------|----------|
| `normal` | Returns fixture data with `source: "fake"` and `asOf` set to current time |
| `empty` | Returns empty/minimal data (empty lists, minimal maps) |
| `error` | Throws `OperationalApiException(code: "operational.unavailable")` |
| `stale` | Returns fixture data with `asOf` set to 10 minutes ago (triggers freshness warning) |

### Fixture files

Located under `services/voice-gateway/src/test/resources/fixtures/operational/`:

- `health.json` — sample health summary payload
- `alerts.json` — sample alerts/events list

Fixtures MUST be valid JSON and match the shapes returned by `OperationalResult.data`.

---

## Orchestrator integration rules

1. Operational facts MUST come **only** from `OperationalResult` payloads.
2. The orchestrator MUST include `asOf` timestamp when answering operational questions.
3. If `asOf` is older than ~3 minutes, the orchestrator MUST qualify the answer (e.g., "As of 5 minutes ago...").
4. If the adapter returns empty data, the orchestrator MUST NOT invent fields — it states the data is unavailable.
5. If the adapter throws an error, the orchestrator reports the gap honestly.

---

## Spec traceability

| Spec reference | Coverage |
|----------------|----------|
| §6 backend <-> operational API | Adapter interface and operations |
| §6 error model (`code`, `message`, `retryable`) | `OperationalApiException` |
| §5 freshness (~3 min) | `asOf` field + orchestrator rules |
| §9 NFR reliability | Error modes + retry guidance |
