# Spec 001: memory_chunks schema and migration

## Summary

Add PostgreSQL table **`memory_chunks`** for cross-turn recall: stores **summaries** or structured snippets tied to `session_id`, optional `source_turn_ids`, and `created_at`. Aligns with `docs/specs/1-initial.md` §5 `MemoryChunk` entity.

**Plan:** [Phase 3](../../1-mvp.md#phase-3-memory)

## Scope

### In scope

- Columns: `id` UUID PK, `session_id` UUID FK → `device_sessions`, `summary` TEXT NOT NULL, `source_turn_ids` UUID[] or JSON array (document), `created_at` TIMESTAMPTZ
- Index `(session_id, created_at DESC)` for retrieval
- Migration file versioned after m1 migrations

### Out of scope

- Vector embedding column
- Per-user auth scope (bonus out of scope)

## Dependencies

- **Prior specs:** [m1/001](../m1-backend-skeleton/001-database-migrations-sessions-turns.md)
- **External:** `DATABASE_URL`

## Interfaces & contracts

### Data & config

- Retention TTL enforced in application layer in m5 (optional delete job); schema supports `created_at` filtering

## Behavior

### Acceptance criteria

1. FK to `device_sessions` enforced ON DELETE CASCADE or RESTRICT—**document choice** (recommend CASCADE for GDPR delete session).
2. `summary` max length **8k** chars enforced in app or DB check.

### Edge cases & errors

- Duplicate insert allowed (multiple chunks per turn batch)

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/db/migration/V2__memory_chunks.sql` | DDL |

## Verification

### Automated

- [ ] Migration test applies on top of m1 schema

### Manual

- [ ] psql `\d memory_chunks`

## Notes

- If PostgreSQL array types are awkward in ORM, use JSONB for `source_turn_ids`.
