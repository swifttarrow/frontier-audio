# Spec 003: Session delete API and retention TTL

## Summary

Implement **configurable retention** for `conversation_turns` and `memory_chunks` (default **30 days** dev per plan assumption) via scheduled job or on-read cleanup, and a **delete-my-data** operation that removes **all** rows for a `sessionId` or `deviceId` as agreed with product.

**Plan:** [Phase 5](../../1-mvp.md#phase-5-hardening-and-evaluation) · **PRD** §13 Q3 pending

## Scope

### In scope

- Env `RETENTION_DAYS` default 30; job runs daily deleting older rows
- **Authenticated** delete not required for MVP; expose **HTTP** `DELETE /v1/devices/{deviceId}/data` protected by **shared secret** header `X-Admin-Key` for evaluators **or** WS message `privacy.purge` with `sessionToken`—**pick one** and document
- Cascade delete turns + memory for target scope
- Audit log line (no PII) counting deleted rows

### Out of scope

- Legal DSAR automation
- Encrypting old backups

## Dependencies

- **Prior specs:** [m3/002](../m3-memory/002-memory-service-save-and-retrieve.md), [m1/001](../m1-backend-skeleton/001-database-migrations-sessions-turns.md)
- **External:** Cron scheduler or embedded coroutine loop

## Interfaces & contracts

### HTTP (example)

- `DELETE /v1/devices/{deviceId}/data`
  - Header: `X-Admin-Key: <env ADMIN_KEY>`
  - Response: `204 No Content` or `200` with `{ "deletedTurns": N, "deletedMemory": M }`

### Data & config

| Env | Purpose |
|-----|---------|
| `RETENTION_DAYS` | TTL |
| `ADMIN_KEY` | Protects delete endpoint |

## Behavior

### Acceptance criteria

1. After delete, subsequent memory recall for that device/session returns empty.
2. Retention job removes only rows older than cutoff.
3. Wrong `ADMIN_KEY` → `403`.

### Edge cases & errors

- Partial failure mid-delete → transaction rollback

## File map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `services/voice-gateway/src/.../privacy/DataLifecycleService.kt` | Delete + TTL |
| Create | `services/voice-gateway/src/.../http/AdminRoutes.kt` | DELETE route |

## Verification

### Automated

- [ ] Integration test: insert old rows, run job, assert removed

### Manual

- [ ] Call delete; verify DB empty for device

## Notes

- Mobile client may expose “clear data” later by calling delete with token—out of MVP if no UI.
