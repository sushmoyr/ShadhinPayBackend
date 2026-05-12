-- Payment Core module — Wave B sub-prompt 8a
-- Owns Transaction (the orchestrator's aggregate), WebhookOutbox (reliable
-- merchant-callback delivery queue), and IdempotencyRecord (24h replay cache).
-- Idempotency is scoped to (business_id, request_key) per Wave B cross-cutting
-- decision #5 — businessId, not merchantId, to honor the multi-business tenancy
-- guarantee. See DOCS/prompts/PHASE_1_WAVE_B_PROMPTS.md §5.

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    amount_value NUMERIC(19, 4) NOT NULL,
    amount_currency CHAR(3) NOT NULL DEFAULT 'BDT',
    status TEXT NOT NULL CHECK (status IN (
        'INITIATED',
        'PENDING',
        'COMPLETED',
        'FAILED',
        'CANCELLED',
        'PENDING_RECOVERY',
        'PENDING_RISK'
    )),
    vendor TEXT NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('PARTNER', 'CUSTOM')),
    merchant_order_reference TEXT NOT NULL,
    vendor_transaction_id TEXT,
    metadata JSONB,
    callback_url TEXT,
    webhook_url TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_transactions_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
);

CREATE INDEX idx_transactions_business_created
    ON transactions (business_id, created_at DESC);

CREATE INDEX idx_transactions_status_pending
    ON transactions (status)
    WHERE status IN ('PENDING_RECOVERY', 'PENDING');

CREATE INDEX idx_transactions_vendor_trx_id
    ON transactions (vendor_transaction_id);

CREATE TABLE webhook_outbox (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    business_id UUID NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN (
        'PAYMENT_INITIATED',
        'PAYMENT_COMPLETED',
        'PAYMENT_FAILED',
        'PAYMENT_REFUNDED',
        'WEBHOOK_PING'
    )),
    payload JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_webhook_outbox_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT
);

CREATE INDEX idx_webhook_outbox_dispatcher_poll
    ON webhook_outbox (status, next_attempt_at);

CREATE TABLE idempotency_records (
    business_id UUID NOT NULL,
    request_key TEXT NOT NULL,
    response_payload JSONB NOT NULL,
    transaction_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotency_records PRIMARY KEY (business_id, request_key)
);

CREATE INDEX idx_idempotency_records_expires_at
    ON idempotency_records (expires_at);
