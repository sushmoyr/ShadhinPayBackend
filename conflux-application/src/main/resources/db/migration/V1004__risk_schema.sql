CREATE TABLE risk_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    expression TEXT NOT NULL,
    score_weight INTEGER NOT NULL,
    action VARCHAR(8) NOT NULL,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted BOOLEAN DEFAULT false NOT NULL
);

CREATE TABLE blacklist_entries (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    value VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    deleted BOOLEAN DEFAULT false NOT NULL
);

CREATE INDEX idx_blacklist_entries_type_value ON blacklist_entries (type, value);

CREATE TABLE merchant_risk_profiles (
    merchant_id UUID PRIMARY KEY,
    trust_level VARCHAR(16) DEFAULT 'NEW' NOT NULL,
    custom_limits TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE TABLE risk_evaluations (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    total_score INTEGER NOT NULL,
    decision VARCHAR(8) NOT NULL,
    triggered_rule_ids JSONB,
    reason TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_risk_evaluations_tx_id ON risk_evaluations (transaction_id);
CREATE INDEX idx_risk_evaluations_merchant_evaluated ON risk_evaluations (merchant_id, evaluated_at DESC);
