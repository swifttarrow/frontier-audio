# Spec 005: CI workflow — test and lint

## Summary

Add **continuous integration** (GitHub Actions recommended) that on **pull request** runs **`make test`** and **`make lint`** for backend and wires Android **lint + unit tests** (instrumented tests optional/skipped on CI if no emulator). Fails fast on main branch protection candidates.

**Plan:** [Phase 5](../../1-mvp.md#phase-5-hardening-and-evaluation)

## Scope

### In scope

- Workflow file `.github/workflows/ci.yml`
- Jobs: `backend` (JDK matrix single version), `android` (SDK 34, run unit tests only if no device)
- Cache Gradle and Maven/dependencies
- Status badge snippet in README (optional)

### Out of scope

- Play internal track upload
- E2E voice in CI without device farm

## Dependencies

- **Prior specs:** [m0/003](../m0-contracts-and-repo-skeleton/003-repo-layout-makefile-tooling.md) — Makefile must invoke real commands
- **External:** GitHub repo with Actions enabled

## Interfaces & contracts

### CI triggers

- `on: [push, pull_request]` paths filter `apps/android/**`, `services/voice-gateway/**`, `Makefile`, workflow file

## Behavior

### Acceptance criteria

1. Failing unit test in backend fails CI.
2. Failing ktlint/spotless/android lint fails CI.
3. Green main is achievable on clean repo post m1–m4.

### Edge cases & errors

- Fork PRs without secrets: use `pull_request_target` only if safe—default `pull_request` with read-only tokens

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `.github/workflows/ci.yml` | Pipeline |

## Verification

### Automated

- [ ] Open test PR with intentional failure; confirm red
- [ ] Revert; confirm green

### Manual

- [ ] Review Actions tab

## Notes

- Self-hosted runners needed for full audio E2E; document as out of CI scope.
