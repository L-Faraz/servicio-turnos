CREATE TABLE authority (
    name VARCHAR(50) PRIMARY KEY
);

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(191) NOT NULL UNIQUE,
    image_url VARCHAR(512),
    lang_key VARCHAR(10) NOT NULL DEFAULT 'es',
    activated BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(50),
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_by VARCHAR(50),
    last_modified_date TIMESTAMPTZ
);

CREATE TABLE user_authority (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    authority_name VARCHAR(50) NOT NULL REFERENCES authority(name),
    PRIMARY KEY (user_id, authority_name)
);

CREATE INDEX idx_app_user_login ON app_user(login);
CREATE INDEX idx_app_user_email ON app_user(email);

INSERT INTO authority(name) VALUES ('ROLE_USER'), ('ROLE_ADMIN'), ('ROLE_SERVICE');
