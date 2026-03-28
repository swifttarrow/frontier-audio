# Spec 001: Database migrations — device_sessions and conversation_turns

## Summary

Add PostgreSQL **schema** and **migrations** for `device_sessions` and `conversation_turns` matching the logical model in `docs/specs/1-initial.md` §5, sufficient for m1 echo pipeline and later m2/m3 extensions without destructive rewrites.

**Plan:** [Phase 1](../../1-mvp.md#phase-1-backend-skeleton)

## Scope

### In scope

- Table **`device_sessions`**: `id` (UUID PK), `device_id` (string, indexed), `session_token_hash` (bytea or string—document choice), `created_at`, `last_active_at`
- Table **`conversation_turns`**: `id` (UUID PK), `session_id` (FK), `client_turn_id` (UUID string, **unique per session**), `role` (enum: `user` | `assistant`), `text` (text, nullable until STT completes), `created_at`, optional `audio_artifact_ref` (nullable)
- Indexes: `(session_id, created_at)`, unique `(session_id, client_turn_id)`
- Migration tool: Flyway, Liquibase, or framework-native (document in file map)

### Out of scope

- `memory_chunks` (m3)
- Read replicas

## Dependencies

- **Prior specs:** [m0/004](../m0-contracts-and-repo-skeleton/004-local-postgres-compose-optional.md) optional for local DB
- **External:** `DATABASE_URL`

## Interfaces & contracts

### Data & config

**`device_sessions`**

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| device_id | TEXT | From client `session.start` |
| session_token_hash | TEXT | Store hash only, never raw token in DB if possible |
| created_at | TIMESTAMPTZ | |
| last_active_at | TIMESTAMPTZ | Updated on each WS activity |

**`conversation_turns`**

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| session_id | UUID | FK → device_sessions |
| client_turn_id | UUID | Idempotency key |
| role | VARCHAR | `user` / `assistant` |
| text | TEXT | |
| audio_artifact_ref | TEXT | nullable |
| created_at | TIMESTAMPTZ | |

## Behavior

### Acceptance criteria

1. Migrations apply cleanly on empty DB and are **reproducible** from CI.
2. Inserting two turns with same `(session_id, client_turn_id)` fails or is handled as no-op per m1-003 spec (coordinate with that spec—**enforce at DB with unique constraint**).
3. Foreign key from `conversation_turns.session_id` to `device_sessions.id` is enforced.

### Edge cases & errors

- Migration downgrade not required for MVP unless team policy requires it—document choice

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/db/migration/V1__sessions_and_turns.sql` (example) | Schema |
| Create | `services/voice-gateway/src/.../DeviceSessionRepository.kt` (or `.py`) | Persistence |

## Verification

### Automated

- [ ] `make test` runs migration against testcontainer or embedded Postgres; repository integration tests pass

### Manual

- [ ] `psql` shows tables after migrate

## Notes

- Token storage: prefer **hash** with pepper from env for anonymous session binding.
