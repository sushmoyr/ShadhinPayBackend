-- Seed 10 shards for each system account
-- Use standard SQL that works in H2 and Postgres

-- Explicitly add unique constraint for H2
ALTER TABLE ledger_accounts ADD CONSTRAINT uk_ledger_accounts_owner_code_shard_curr UNIQUE (owner_id, code, shard_id, currency);

INSERT INTO ledger_accounts (id, owner_id, type, code, shard_id, currency, balance, version, created_at, updated_at)
SELECT 
    random_uuid(), 
    NULL, 
    'ASSET', 
    'ESCROW', 
    v.shard, 
    'BDT', 
    0.0, 
    0, 
    current_timestamp, 
    current_timestamp
FROM (VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)) AS v(shard);

INSERT INTO ledger_accounts (id, owner_id, type, code, shard_id, currency, balance, version, created_at, updated_at)
SELECT 
    random_uuid(), 
    NULL, 
    'REVENUE', 
    'PLATFORM_REVENUE', 
    v.shard, 
    'BDT', 
    0.0, 
    0, 
    current_timestamp, 
    current_timestamp
FROM (VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)) AS v(shard);

INSERT INTO ledger_accounts (id, owner_id, type, code, shard_id, currency, balance, version, created_at, updated_at)
SELECT 
    random_uuid(), 
    NULL, 
    'LIABILITY', 
    'VENDOR_PAYABLE', 
    v.shard, 
    'BDT', 
    0.0, 
    0, 
    current_timestamp, 
    current_timestamp
FROM (VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)) AS v(shard);
