# Spec 001: Gateway rate limits per device and IP

## Summary

Protect the voice gateway from abuse and runaway cost by enforcing **rate limits** on **WebSocket connect**, **`session.start`**, and **`audio.commit`** events keyed by **`deviceId`** and client **IP** (from proxy headers if behind load balancer). Return structured **`error`** with `retryable: true` and `code: rate_limited` when exceeded.

**Plan:** [Phase 5](../../1-mvp.md#phase-5-hardening-and-evaluation) · **Spec:** `docs/specs/1-initial.md` §6

## Scope

### In scope

- In-memory token bucket per `deviceId` (simple) or Redis if multi-instance—**document** single-node assumption for MVP
- Default limits (tunable via env): e.g. **30** commits/hour/device, **10** connects/hour/IP
- **429**-style semantics mapped to WS `error` payload
- Log line when throttled with `sessionId` if known

### Out of scope

- Paid tier differentiation
- Global DDoS scrubbing

## Dependencies

- **Prior specs:** [m1/003](../m1-backend-skeleton/003-websocket-session-handshake.md)
- **External:** Optional Redis URL for multi-instance

## Interfaces & contracts

### Data & config

| Env | Default | Purpose |
|-----|---------|---------|
| `RATE_LIMIT_COMMITS_PER_HOUR` | 30 | Per deviceId |
| `RATE_LIMIT_CONNECTS_PER_HOUR` | 10 | Per IP |

## Behavior

### Acceptance criteria

1. Burst over limit returns `error` frame before expensive STT runs.
2. Limits reset per sliding or fixed window—document which.
3. Legitimate user under limit unaffected (integration test).

### Edge cases & errors

- Missing `X-Forwarded-For` trust: only use when `TRUST_PROXY_HEADERS=true`

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../limits/RateLimiter.kt` | Core |
| Modify | `services/voice-gateway/.../VoiceWebSocketHandler.kt` | Enforce |

## Verification

### Automated

- [ ] Unit tests: boundary at N and N+1

### Manual

- [ ] Script hammers endpoint; sees rate_limited

## Notes

- Coordinate message text with m2-004 for spoken apology if desired.
