-- V2: Create memory_chunks table for cross-turn recall
-- Spec: docs/specs/1-initial.md §5 MemoryChunk entity

CREATE TABLE memory_chunks (
    id              UUID PRIMARY KEY,
    session_id      UUID NOT NULL REFERENCES device_sessions(id) ON DELETE CASCADE,
    summary         TEXT NOT NULL,
    source_turn_ids JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_chunks_session_created
    ON memory_chunks (session_id, created_at DESC);
