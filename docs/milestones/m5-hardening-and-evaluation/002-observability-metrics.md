# Spec 002: Observability — metrics and log completeness

## Summary

Expose **Prometheus** metrics (or equivalent) for **STT latency**, **TTS time-to-first-byte**, **tool call failures** by code, **interrupt count**, and **active WebSocket connections**. Ensure logs from m1-005 remain **PII-safe** at INFO. Optionally add **OpenTelemetry** traces around STT/TTS/tool calls.

**Plan:** [Phase 5](../../1-mvp.md#phase-5-hardening-and-evaluation) · **Spec:** `docs/specs/1-initial.md` §9

## Scope

### In scope

- `/metrics` HTTP endpoint on admin port or same server with auth off in dev only
- Metric names: `jarvis_stt_seconds`, `jarvis_tts_ttfb_seconds`, `jarvis_tool_errors_total{code}`, `jarvis_interrupts_total`, `jarvis_ws_active`
- Histogram buckets documented for voice latencies
- Grafana/dashboard JSON optional in `docs/ops/` (not required)

### Out of scope

- Log shipping to vendor (document in README only)

## Dependencies

- **Prior specs:** [m1/005](../m1-backend-skeleton/005-structured-logging-correlation.md), [m2/003](../m2-tools-github-operational-api/003-llm-orchestrator-tool-calling.md)
- **External:** Prometheus scrape config for evaluators

## Interfaces & contracts

### HTTP

- `GET /metrics` — Prometheus text format (dev: open, prod: restrict—document)

## Behavior

### Acceptance criteria

1. Each completed turn increments STT histogram with observed duration.
2. Tool failure increments counter with label matching `UserFacingError.code`.
3. `jarvis_ws_active` reflects current connections ±1 in load test.

### Edge cases & errors

- Metrics collection must not block voice path (async record)

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../observability/Metrics.kt` | Registry |
| Modify | `TurnPipeline.kt` / orchestrator | Record timings |

## Verification

### Automated

- [ ] Unit test: metric registry contains expected names after simulated turn

### Manual

- [ ] `curl localhost:8081/metrics | grep jarvis_`

## Notes

- OpenTelemetry: add only if team already runs collector.
