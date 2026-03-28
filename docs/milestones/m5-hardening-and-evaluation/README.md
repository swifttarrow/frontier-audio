# Milestone 5: Hardening and evaluation

## Overview

Add **per-device/IP rate limiting** on voice endpoints, **observability** (metrics + structured logs baseline extension), **session delete / retention TTL** hooks per policy assumption, **evaluation** scenario doc (and optional CI), **root README** for evaluators, and **CI** running `make test` + `make lint` if not already present.

**Plan reference:** [Phase 5: Hardening and evaluation](../../1-mvp.md#phase-5-hardening-and-evaluation)

## Dependencies

- [ ] Milestones 1–4 — functional system exists

## Changes Required

- Gateway throttling (token bucket or fixed window)
- Metrics: STT latency, TTS TTFB, tool errors, interrupt count (Prometheus or cloud vendor)
- `DELETE /v1/sessions/{id}` or WS `session.purge` — **pick one** for “delete my data”
- `docs/eval/mvp-scenarios.md` or `eval/` golden list
- GitHub Actions (or other) workflow

## Success Criteria

### Automated Verification

- [ ] `make test` full suite green in CI
- [ ] `make lint` full workspace green

### Manual Verification

- [ ] PRD-aligned scenario checklist completed
- [ ] Stale cache fixture produces **~3 min** freshness qualification in assistant text

## Implementation specs

- [001-gateway-rate-limits](./001-gateway-rate-limits.md)
- [002-observability-metrics](./002-observability-metrics.md)
- [003-session-delete-and-retention](./003-session-delete-and-retention.md)
- [004-eval-scenarios-and-readme](./004-eval-scenarios-and-readme.md)
- [005-ci-workflow](./005-ci-workflow.md)
