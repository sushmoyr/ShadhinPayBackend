-- Provisioning module — Wave B sub-prompt 7a
-- Owns Business, VendorConfig, ApiKey. Encrypted columns hold AES-256-GCM ciphertexts;
-- decryption is never performed in SQL.

CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    logo_url TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    webhook_url TEXT,
    webhook_secret_encrypted TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_businesses_merchant FOREIGN KEY (merchant_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_businesses_merchant_deleted ON businesses (merchant_id, deleted);

CREATE TABLE vendor_configs (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    vendor TEXT NOT NULL CHECK (vendor IN ('BKASH', 'NAGAD', 'ROCKET', 'UPAY', 'PATHAO', 'MCASH', 'STRIPE', 'MOCK')),
    mode TEXT NOT NULL DEFAULT 'PARTNER' CHECK (mode IN ('PARTNER', 'CUSTOM')),
    credentials_encrypted JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_vendor_configs_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_vendor_configs_business_vendor ON vendor_configs (business_id, vendor);

CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    key_hash TEXT NOT NULL,
    key_prefix TEXT NOT NULL,
    last_four TEXT NOT NULL,
    environment TEXT NOT NULL CHECK (environment IN ('TEST', 'LIVE')),
    revoked BOOLEAN NOT NULL DEFAULT false,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_api_keys_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_api_keys_key_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_business_env_revoked ON api_keys (business_id, environment, revoked);
