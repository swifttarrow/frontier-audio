# Spec 003: Repository layout, Makefile, and baseline tooling

## Summary

Create the **monorepo skeleton** and root **`Makefile`** so `make test` and `make lint` exist from day one (placeholders OK), plus baseline **`.editorconfig`** and **`.gitignore`** for Android + JVM/Python backend. This unlocks consistent automation in later milestones.

**Plan:** [Phase 0](../../1-mvp.md#phase-0-contracts-and-repository-skeleton)

## Scope

### In scope

- Directory layout (document in root `README.md` fragment or `docs/`): e.g. `apps/android/`, `services/voice-gateway/`
- `Makefile` targets:
  - `make test` — invokes backend tests if present, else `echo "no tests yet"`; m1 must replace with real command
  - `make lint` — same pattern
  - Optional: `make fmt` stub
- Android: Gradle wrapper committed or documented generation steps
- Backend: build file (`build.gradle.kts` / Maven / `pyproject.toml`) minimal **hello** module
- `.gitignore`: Android (`build/`, `.gradle/`, `local.properties`), IDE, Python (`__pycache__/`, `.venv/`), env files (`.env`)

### Out of scope

- CI workflows (m5)
- Production Docker image for gateway

## Dependencies

- **Prior specs:** None
- **External:** JDK version, Android SDK (document in root README under “Prerequisites”)

## Interfaces & contracts

### CLI

| Target | Contract |
|--------|----------|
| `make test` | Exit 0; must not delete user data |
| `make lint` | Exit 0 with placeholders |

### Data & config

- No secrets in repo; `.env.example` listing `GITHUB_TOKEN`, `OPENAI_API_KEY`, `DATABASE_URL` as **names only** (optional in this spec)

## Behavior

### Acceptance criteria

1. Fresh clone + documented JDK/Android prereqs allows `make test && make lint` to succeed.
2. `apps/android` contains a **buildable** empty activity or “Hello Jarvis” screen (optional minimal) OR a Gradle project that syncs—**pick one** and document.
3. `services/voice-gateway` contains a runnable entrypoint (e.g. Ktor `main` returning 200 on `GET /health`) OR documented stub.

### Edge cases & errors

- Missing local JDK → README points to install; Makefile may print hint on failure

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `Makefile` | test/lint aggregation |
| Create | `apps/android/` | Android project scaffold |
| Create | `services/voice-gateway/` | Backend scaffold |
| Modify | `.gitignore` | coverage for stacks |
| Create or modify | `.editorconfig` | indent/charset defaults |
| Modify | `README.md` (repo root) | Prerequisites + layout (if file exists; create if greenfield) |

## Verification

### Automated

- [ ] `make test` exit 0
- [ ] `make lint` exit 0

### Manual

- [ ] Android Studio / Gradle sync succeeds for `apps/android`
- [ ] Backend module runs or tests pass per chosen stack

## Notes

- **Pick backend stack at kickoff** (`docs/plans/1-mvp.md` assumption): Ktor vs FastAPI—file map above assumes `services/voice-gateway/` name is stable either way.
