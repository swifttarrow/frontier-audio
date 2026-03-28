-- V1: Create device_sessions and conversation_turns tables
-- Spec: docs/specs/1-initial.md §5

CREATE TABLE device_sessions (
    id              UUID PRIMARY KEY,
    device_id       TEXT NOT NULL,
    session_token_hash TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_sessions_device_id ON device_sessions (device_id);

CREATE TABLE conversation_turns (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL REFERENCES device_sessions(id),
    client_turn_id    UUID NOT NULL,
    role              VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    text              TEXT,
    audio_artifact_ref TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_session_client_turn UNIQUE (session_id, client_turn_id, role)
);

CREATE INDEX idx_conversation_turns_session_created
    ON conversation_turns (session_id, created_at);
