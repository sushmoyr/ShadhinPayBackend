CREATE TABLE users (
    id UUID PRIMARY KEY,
    identifier VARCHAR(255) NOT NULL,
    identifier_type VARCHAR(16) NOT NULL,
    password_hash VARCHAR(72) NOT NULL,
    user_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    mfa_secret TEXT NULL,
    last_login_at TIMESTAMPTZ NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_users_identifier_type ON users (identifier, identifier_type) WHERE deleted = false;

CREATE TABLE merchant_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    onboarding_status VARCHAR(32) NOT NULL,
    kyc_data TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_merchant_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE admin_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    department VARCHAR(255) NOT NULL,
    employee_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_admin_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);
