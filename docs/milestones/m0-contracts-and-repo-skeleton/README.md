# Milestone 0: Contracts and repository skeleton

## Overview

Lock **WebSocket message schema** (JSON control + binary audio framing), **session/device auth handshake**, **operational API adapter interface** (documented contract for a stub), **IntegrationConfig** shape (`defaultPublicRepoUrl`, `operationalApiBaseUrl`), and **repo layout** so later milestones do not rework boundaries.

**Plan reference:** [Phase 0: Contracts and repository skeleton](../../1-mvp.md#phase-0-contracts-and-repository-skeleton)

## Dependencies

- [ ] None (greenfield entry)

## Changes Required

- Author `docs/contracts/websocket-protocol.md` with message types: `session.start`, `audio.frame` / end-of-utterance, `interrupt`, `session.end`, error envelopes, `clientTurnId`.
- Author `docs/contracts/operational-api-adapter.md` with adapter responsibilities, error mapping, `asOf`/cache timestamp requirement (`docs/specs/1-initial.md` §6).
- Add root **`Makefile`** with `test` and `lint` (placeholders acceptable until m1 wires commands).
- Create monorepo directories: e.g. `apps/android/`, `services/voice-gateway/` — scaffold only (hello-world).
- Update `.editorconfig` / `.gitignore` as needed for chosen stacks.
- Optional: `docker-compose.yml` for local PostgreSQL.

## Success Criteria

### Automated Verification

- [ ] `make test` runs (no-op or placeholder acceptable)
- [ ] `make lint` runs (placeholder acceptable)

### Manual Verification

- [ ] Reviewer can read contracts and trace each spec §6 operation in `docs/specs/1-initial.md` to a message or documented HTTP route
- [ ] Assumptions in `docs/plans/1-mvp.md` remain reflected or explicitly deferred

## Implementation specs

- [001-websocket-protocol-contract](./001-websocket-protocol-contract.md)
- [002-operational-api-adapter-contract](./002-operational-api-adapter-contract.md)
- [003-repo-layout-makefile-tooling](./003-repo-layout-makefile-tooling.md)
- [004-local-postgres-compose-optional](./004-local-postgres-compose-optional.md)
