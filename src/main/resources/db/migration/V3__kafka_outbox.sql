ALTER TABLE reservation_process
    ADD COLUMN phone_attempt INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN patient_phone VARCHAR(30);

CREATE TABLE processed_event (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    event_id VARCHAR(120) PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_pending ON outbox_event(status, next_attempt_at);
CREATE INDEX idx_processed_event_type ON processed_event(event_type);
