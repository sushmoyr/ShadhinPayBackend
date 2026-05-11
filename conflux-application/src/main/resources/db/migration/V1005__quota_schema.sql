CREATE TABLE IF NOT EXISTS quota_usage (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    period VARCHAR(7) NOT NULL,
    partner_mode_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_quota_usage_merchant_period UNIQUE (merchant_id, period)
);