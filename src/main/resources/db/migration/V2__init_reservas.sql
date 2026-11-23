CREATE TABLE reservation_process (
    process_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    hold_id VARCHAR(64),
    reservation_id BIGINT,
    professional_id BIGINT NOT NULL,
    professional_first_name VARCHAR(50),
    professional_last_name VARCHAR(50),
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    external_patient_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_event_id VARCHAR(100),
    expires_at TIMESTAMPTZ,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE appointment_hold (
    hold_id VARCHAR(64) PRIMARY KEY,
    process_id VARCHAR(64) NOT NULL REFERENCES reservation_process(process_id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    professional_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_reservation_process_hold_id ON reservation_process(hold_id);
CREATE INDEX idx_reservation_process_user_status ON reservation_process(user_id, status);
CREATE INDEX idx_reservation_process_expiration ON reservation_process(expires_at);
CREATE INDEX idx_appointment_hold_user_status ON appointment_hold(user_id, status);
