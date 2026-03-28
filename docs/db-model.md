# Relational database model (Jarvis MVP)

This document captures the PostgreSQL schema derived from the technical specification [`docs/specs/1-initial.md`](specs/1-initial.md), aligned with milestone specs [`docs/milestones/m1-backend-skeleton/001-database-migrations-sessions-turns.md`](milestones/m1-backend-skeleton/001-database-migrations-sessions-turns.md) and [`docs/milestones/m3-memory/001-memory-chunks-schema.md`](milestones/m3-memory/001-memory-chunks-schema.md). It was produced using the workflow in [`agent/prompts/modeling.md`](../agent/prompts/modeling.md).

---

## 1. Tech spec source used

- **Primary:** `docs/specs/1-initial.md` — scope, §5 data model, APIs, auth, retention notes.
- **Secondary:** m1/001 and m3/001 milestone specs — column-level locks for sessions, turns, and memory chunks.
- **PRD:** Not duplicated here; the spec maps PRD anchors. Open items (retention, legal delete) stay **TBD** per spec §11 and §5.

**Persistence summary:** PostgreSQL for sessions, turns, memory, and server-side integration config; device-scoped identity; `clientTurnId` idempotency; optional `audio_artifact_ref`; no long-term raw audio in DB by default; observability via logs/metrics (no extra audit tables required by spec).

---

## 2. Schema assumptions

| Assumption | Grounding |
|------------|-----------|
| Surrogate keys | UUID `id` on all tables; spec uses `sessionId` / `turnId` as logical IDs. |
| `device_id` | Opaque string from `session.start` (install-bound / device fingerprint); not interpreted in DB. |
| Session token | Store **hash only** (m1); raw token never persisted. |
| `client_turn_id` on every row | m1 unique `(session_id, client_turn_id)`: user turn uses client UUID; assistant turn uses a **server-issued** UUID (or deterministic derivative) so user and assistant rows never share the same pair. |
| `conversation_turns.text` nullable | m1: nullable until STT completes. |
| `memory_chunks.source_turn_ids` | UUID array per m3; referential integrity to turns is **app-enforced** (arrays cannot FK easily). |
| `integration_config` | Spec lists logical IntegrationConfig; modeled as a DB table for remote config / versioning and `session.start` capabilities. Alternative: env-only without this table — ops choice. |
| Rate limits | Spec: per `deviceId` / IP — **not** modeled as Postgres tables (gateway/cache). |
| Retention / delete-my-data | **TBD** (spec §11 Q3); schema uses `ON DELETE CASCADE` from session so a future “wipe session” is one delete. |
| No user accounts | Bonus sign-in out of scope; no `users` table. |

---

## 3. Entity list

| Table | Role |
|-------|------|
| `device_sessions` | Anonymous device-bound session, token hash, activity timestamps. |
| `conversation_turns` | User/assistant turns, idempotent `client_turn_id`, text, optional audio ref. |
| `memory_chunks` | Summaries/snippets for recall, optional provenance to turns. |
| `integration_config` | Default repo URL, API base, display name, refresh metadata, config version. |

---

## 4. Relational schema

### `device_sessions`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|--------|
| `id` | `UUID` | NO | `gen_random_uuid()` | PK = `sessionId` |
| `device_id` | `TEXT` | NO | — | From client |
| `session_token_hash` | `TEXT` | NO | — | Hashed token (pepper in app) |
| `created_at` | `TIMESTAMPTZ` | NO | `now()` | |
| `last_active_at` | `TIMESTAMPTZ` | NO | `now()` | Bump on activity |

- **PK:** `id`
- **FK:** none
- **Indexes:** `device_id`; optional `last_active_at` for sweeps

### `conversation_turns`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|--------|
| `id` | `UUID` | NO | `gen_random_uuid()` | PK = `turnId` |
| `session_id` | `UUID` | NO | — | FK → `device_sessions` |
| `client_turn_id` | `UUID` | NO | — | Idempotency |
| `role` | `turn_role` | NO | — | enum |
| `text` | `TEXT` | YES | — | Post-STT / model |
| `audio_artifact_ref` | `TEXT` | YES | — | Object storage key/URI |
| `created_at` | `TIMESTAMPTZ` | NO | `now()` | |

- **PK:** `id`
- **FK:** `session_id` → `device_sessions(id)` **ON DELETE CASCADE**
- **Unique:** `(session_id, client_turn_id)`
- **Indexes:** `(session_id, created_at)`

### `memory_chunks`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|--------|
| `id` | `UUID` | NO | `gen_random_uuid()` | |
| `session_id` | `UUID` | NO | — | FK → `device_sessions` |
| `summary` | `TEXT` | NO | — | Max 8k (m3) |
| `source_turn_ids` | `UUID[]` | NO | `'{}'` | Provenance |
| `created_at` | `TIMESTAMPTZ` | NO | `now()` | |

- **PK:** `id`
- **FK:** `session_id` → `device_sessions(id)` **ON DELETE CASCADE**
- **Indexes:** `(session_id, created_at DESC)`
- **Check:** `char_length(summary) <= 8000`

### `integration_config`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|--------|
| `id` | `UUID` | NO | `gen_random_uuid()` | |
| `singleton_key` | `TEXT` | NO | `'default'` | Single-tenant MVP |
| `default_public_repo_url` | `TEXT` | NO | — | Not logged as secret in app |
| `operational_api_base_url` | `TEXT` | NO | — | |
| `repo_display_name` | `TEXT` | NO | — | Safe for `session.start` |
| `refresh_policy_metadata` | `JSONB` | NO | `'{}'` | TTL hints, etc. |
| `version` | `INT` | NO | `1` | Bumped when config changes |
| `updated_at` | `TIMESTAMPTZ` | NO | `now()` | |

- **PK:** `id`
- **Unique:** `singleton_key`

---

## 5. Relationship summary

- **device_sessions → conversation_turns:** one-to-many.
- **device_sessions → memory_chunks:** one-to-many.
- **integration_config:** standalone (no FK to sessions).
- **memory_chunks → conversation_turns:** logical only via `source_turn_ids` (array); no FK.

---

## 6. Enums / lookup tables

- **`turn_role`:** PostgreSQL `ENUM ('user', 'assistant')` — stable, small, indexed; matches m1.
- **No lookup tables** required for MVP entities in §5.

---

## 7. Audit / history strategy

Not required by spec for a dedicated audit table. §9 calls for structured logs with `sessionId` / `turnId` — operational concern, not a relational audit log.

---

## 8. Soft delete / archival strategy

**TBD** with legal/product (spec §5 retention). **Recommendation:** hard delete `device_sessions` → **CASCADE** removes turns and memory for “delete my data” once policy is fixed. Soft delete (`deleted_at`) only if tombstones or phased purge are required.

---

## 9. Permission-sensitive data notes

- **Tokens:** hash + pepper; no raw session token in DB.
- **Transcripts:** `conversation_turns.text` and `memory_chunks.summary` are sensitive; encrypt at rest (ops), restrict DB access, avoid PII in logs (§9).
- **URLs in `integration_config`:** treat as secrets-adjacent; do not log full URLs in clear text.
- **Session hijack:** entropy and binding via `session_token_hash` + TLS (§9).

---

## 10. Migration order

1. Extension if required by Postgres version for `gen_random_uuid()` (often built-in).
2. `CREATE TYPE turn_role AS ENUM (...)`
3. `device_sessions`
4. `integration_config` (no FK dependencies)
5. `conversation_turns`
6. `memory_chunks`

---

## 11. SQL DDL

```sql
-- On older Postgres, if needed:
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE turn_role AS ENUM ('user', 'assistant');

CREATE TABLE device_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id TEXT NOT NULL,
  session_token_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_active_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_sessions_device_id ON device_sessions (device_id);

CREATE TABLE integration_config (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  singleton_key TEXT NOT NULL DEFAULT 'default' UNIQUE,
  default_public_repo_url TEXT NOT NULL,
  operational_api_base_url TEXT NOT NULL,
  repo_display_name TEXT NOT NULL,
  refresh_policy_metadata JSONB NOT NULL DEFAULT '{}',
  version INT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_turns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES device_sessions (id) ON DELETE CASCADE,
  client_turn_id UUID NOT NULL,
  role turn_role NOT NULL,
  text TEXT,
  audio_artifact_ref TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, client_turn_id)
);

CREATE INDEX idx_conversation_turns_session_created
  ON conversation_turns (session_id, created_at);

CREATE TABLE memory_chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES device_sessions (id) ON DELETE CASCADE,
  summary TEXT NOT NULL,
  source_turn_ids UUID[] NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT memory_chunks_summary_len CHECK (char_length(summary) <= 8000)
);

CREATE INDEX idx_memory_chunks_session_created
  ON memory_chunks (session_id, created_at DESC);
```

---

## 12. Spec traceability

| Artifact | Spec / milestone |
|----------|-------------------|
| `device_sessions` | §5 DeviceSession; §7 device-bound session; m1/001 |
| `conversation_turns` | §5 ConversationTurn; §6 `clientTurnId`; m1/001 |
| `memory_chunks` | §5 MemoryChunk; §8.3 recall; m3/001 |
| `integration_config` | §5 IntegrationConfig; §6 `session.start` / repo display; §4 Configuration |
| CASCADE from session | §5 retention/delete TBD + m3 delete-session note |

---

## 13. Future-proofing notes

- Add **`user_id`** (nullable) on `device_sessions` when bonus auth exists; keep `device_id` for migration.
- **Structured memory:** if facts move beyond flat text, add `JSONB` or a child table without dropping `summary`.
- **Vector search:** m3 explicitly out of scope; add column/migration later if needed.
- **Multi-tenant config:** replace singleton `integration_config` with `tenant_id` or env-based partitioning.

---

## Design decisions (spec-driven)

| Topic | Decision |
|-------|----------|
| Identity | UUID session id; opaque `device_id`; no user table in MVP. |
| Idempotency | `UNIQUE (session_id, client_turn_id)`; assistant `client_turn_id` server-issued. |
| Retention | No TTL columns until product defines policy; purge via `DELETE` session. |
| Blobs | `audio_artifact_ref` only; no raw audio in DB per §5. |
| Config | Table for versioned server config; env can override in app if desired. |
| Concurrency | Single row `integration_config` per key — use `version` + optimistic update in app if multiple writers. |

---

## Review checklist

1. **Normalization:** `source_turn_ids` as array duplicates turn ids; a join table would enforce FKs but adds write complexity — acceptable MVP trade per m3.
2. **Performance:** Hot paths: `(session_id, created_at)` on turns; `(session_id, created_at DESC)` on memory. Add GIN on `source_turn_ids` only if querying by turn id.
3. **Indexes:** Consider partial index on `device_sessions (last_active_at)` if session expiry sweeps run at scale.
4. **DB vs app:** Turn ordering, STT completion, and “valid `source_turn_ids` belong to session” — app (or trigger for strict integrity later).
5. **Premature:** Persisted rate-limit counters, embedding columns, read replicas — not for current spec.
6. **Open clarifications:** Retention/delete (§11 Q3); DB vs env-only for `integration_config` (§11 Q1); assistant `client_turn_id` strategy locked in WebSocket/protocol docs.

---

## Verification

1. Apply DDL to a scratch Postgres instance.
2. Insert one `device_sessions` row, two `conversation_turns` with distinct `client_turn_id` values, one `memory_chunk`.
3. Confirm duplicate `(session_id, client_turn_id)` is rejected.
4. Delete the session and confirm turns and memory rows are removed via `ON DELETE CASCADE`.
