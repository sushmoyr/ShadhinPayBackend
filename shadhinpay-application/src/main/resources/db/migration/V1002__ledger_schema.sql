-- Ledger Schema V1002

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NULL,
    type VARCHAR(16) NOT NULL,
    code VARCHAR(64) NOT NULL,
    shard_id INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'BDT',
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Note: Using Postgres 15+ UNIQUE NULLS NOT DISTINCT
-- This correctly treats NULL owner_id values as equal for system accounts,
-- ensuring we only have one account per code+shard for the system.
CREATE UNIQUE INDEX uk_ledger_accounts_owner_code_shard_curr
    ON ledger_accounts (owner_id NULLS NOT DISTINCT, code, shard_id, currency);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_journal_entries_source UNIQUE (source_type, source_id)
);

CREATE TABLE postings (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL REFERENCES journal_entries (id),
    account_id UUID NOT NULL REFERENCES ledger_accounts (id),
    amount NUMERIC(19,4) NOT NULL,
    type VARCHAR(8) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_postings_account_id_created_at ON postings (account_id, created_at);
CREATE INDEX idx_postings_journal_id ON postings (journal_id);
