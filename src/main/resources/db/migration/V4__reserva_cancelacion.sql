ALTER TABLE reservation_process
    ALTER COLUMN reason TYPE VARCHAR(500),
    ADD COLUMN cancelled_at TIMESTAMPTZ;
