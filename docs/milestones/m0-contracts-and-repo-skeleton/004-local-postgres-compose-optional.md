# Spec 004: Local PostgreSQL via Docker Compose (optional)

## Summary

Add an **optional** `docker-compose.yml` that runs PostgreSQL for local development of m1+ migrations. Developers who prefer a managed DB can skip this file; CI may use the same compose for integration tests later.

**Plan:** [Phase 0](../../1-mvp.md#phase-0-contracts-and-repository-skeleton) — optional item

## Scope

### In scope

- `docker-compose.yml` service `postgres` with:
  - Published port `5432:5432` (or documented alternate to avoid conflicts)
  - Database name, user, password as **non-production** defaults
  - Named volume for data persistence
- `.env.example` entry `DATABASE_URL=postgresql://user:pass@localhost:5432/jarvis_dev`
- One-paragraph doc in root `README.md`: how to `docker compose up -d` and connect

### Out of scope

- Production database HA
- Migration runner inside compose (m1 owns flyway/liquibase execution)

## Dependencies

- **Prior specs:** None
- **External:** Docker Desktop or compatible runtime

## Interfaces & contracts

### Data & config

| Var | Purpose |
|-----|---------|
| `POSTGRES_USER` | compose default `jarvis` |
| `POSTGRES_PASSWORD` | compose default `jarvis` (local only) |
| `POSTGRES_DB` | `jarvis_dev` |

## Behavior

### Acceptance criteria

1. `docker compose up -d` starts Postgres reachable on documented port.
2. `DATABASE_URL` in `.env.example` matches compose credentials.
3. `docker compose down -v` is documented as “wipes local data.”

### Edge cases & errors

- Port 5432 already in use → README suggests changing host port mapping

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `docker-compose.yml` | Local Postgres |
| Modify | `.env.example` | `DATABASE_URL` |

## Verification

### Automated

- [ ] Optional: CI job `docker compose config` validation (m5)

### Manual

- [ ] `psql` or GUI connects with URL from `.env.example`

## Notes

- If team bans Docker, mark this spec **skipped** in milestone README and use cloud dev DB instead.
