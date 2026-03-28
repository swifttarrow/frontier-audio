# Milestone 2: Tools — GitHub and operational API

## Overview

Add **GitHub client** (public repo only) with **rate-limit handling** and **caching**, **operational API adapter** with **fake** implementation per contract, **LLM orchestrator** with **tool calling** so factual claims trace to tool JSON, **~3 minute freshness** hints via `asOf`, and **structured errors** (`code`, `message`, `retryable`).

**Plan reference:** [Phase 2: Tools — GitHub and operational API](../../1-mvp.md#phase-2-tools--github-and-operational-api)

## Dependencies

- [ ] Milestone 1 — WebSocket + STT/TTS + sessions + `IntegrationConfig`

## Changes Required

- GitHub REST (or GraphQL) read operations aligned with `docs/research/2026-03-26-github-integration-contract.md` where compatible
- `OperationalApiAdapter` fake + interface per `docs/contracts/operational-api-adapter.md`
- Replace echo pipeline reply path with **LLM + tools**; system prompt enforces no fabrication
- Map tool/GitHub failures to WS errors or assistant spoken fallback per policy

## Success Criteria

### Automated Verification

- [ ] `make test` — GitHub fixtures or recorded tests; fake API golden tests; empty/error tool tests
- [ ] `make lint`

### Manual Verification

- [ ] Question triggers tool fetch; answer fields match payload only
- [ ] Simulated GitHub 403/rate limit surfaces user-visible retry guidance

## Implementation specs

- [001-github-client-caching-ratelimit](./001-github-client-caching-ratelimit.md)
- [002-operational-adapter-fake](./002-operational-adapter-fake.md)
- [003-llm-orchestrator-tool-calling](./003-llm-orchestrator-tool-calling.md)
- [004-tool-error-envelopes-and-user-messaging](./004-tool-error-envelopes-and-user-messaging.md)
