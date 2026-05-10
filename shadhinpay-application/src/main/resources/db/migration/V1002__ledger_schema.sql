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
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Unique constraint for accounts: one account per owner/code/shard/currency.
-- For system accounts (owner_id is NULL), we use a sentinel UUID to ensure uniqueness.
CREATE UNIQUE INDEX uk_ledger_accounts_owner_code_shard_curr
    ON ledger_accounts (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'), code, shard_id, currency);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_journal_entries_source UNIQUE (source_type, source_id)
);

CREATE TABLE postings (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL REFERENCES journal_entries (id),
    account_id UUID NOT NULL REFERENCES ledger_accounts (id),
    amount NUMERIC(19,4) NOT NULL,
    type VARCHAR(8) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_postings_account_id_created_at ON postings (account_id, created_at);
CREATE INDEX idx_postings_journal_id ON postings (journal_id);
