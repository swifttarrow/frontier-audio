# Spec 002: IntegrationConfig — default public GitHub repo and API base

## Summary

Load **server-side** configuration for `defaultPublicRepoUrl` and `operationalApiBaseUrl` (and optional display name) so m2 GitHub tools and the operational adapter can read **without** any mobile keyboard input. m1 exposes `repoDisplayName` in `session.ack` per WebSocket contract.

**Plan:** [Phase 1](../../1-mvp.md#phase-1-backend-skeleton)

## Scope

### In scope

- Config sources (priority order documented): environment variables → optional DB row `integration_config` (single-tenant MVP)
- Validation: `defaultPublicRepoUrl` MUST be `https://github.com/{owner}/{repo}` pattern (reject `file://`, private hostnames in MVP if desired)
- Hot-reload not required; restart to pick up env changes is OK
- Surface in `session.ack` as `repoDisplayName` (e.g. `owner/repo`)

### Out of scope

- Per-user repo selection UI
- Remote config service

## Dependencies

- **Prior specs:** [m0/001](../m0-contracts-and-repo-skeleton/001-websocket-protocol-contract.md) (`session.ack` fields)
- **External:** None

## Interfaces & contracts

### Data & config

| Env var | Required | Purpose |
|---------|----------|---------|
| `JARVIS_DEFAULT_GITHUB_REPO_URL` | yes (m2+) | Public HTTPS GitHub URL |
| `OPERATIONAL_API_BASE_URL` | no | m2 fake vs real |
| `JARVIS_REPO_DISPLAY_NAME` | no | Override display; else parse from URL |

### Public API

- `IntegrationConfigProvider` (code): `fun load(): IntegrationConfig`
- `data class IntegrationConfig(val defaultPublicRepoUrl: String, val repoDisplayName: String, val operationalApiBaseUrl: String?)`

## Behavior

### Acceptance criteria

1. Missing `JARVIS_DEFAULT_GITHUB_REPO_URL` in **prod** profile fails fast at startup with clear error.
2. `repoDisplayName` never contains tokens or PATs.
3. Invalid URL format fails startup or logs ERROR and refuses `session.start` with `error` WS message—**pick one** and test.

### Edge cases & errors

- Trailing slash on GitHub URL normalized

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../config/IntegrationConfig.kt` | Load + validate |
| Modify | `.env.example` | Document env vars |

## Verification

### Automated

- [ ] Unit tests: valid URL parses display name; invalid URL rejected

### Manual

- [ ] `session.ack` shows expected `repoDisplayName` when client connects

## Notes

- Aligns with `docs/plans/1-mvp.md` assumption: repo URL without keyboard.
