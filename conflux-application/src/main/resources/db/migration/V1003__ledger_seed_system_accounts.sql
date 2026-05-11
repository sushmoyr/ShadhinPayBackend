-- Seed System Accounts V1003
-- Creates 10 shards (0-9) each for ESCROW, PLATFORM_REVENUE, and VENDOR_PAYABLE.
-- Using gen_random_uuid() for simplicity; this means rerunning migrations from scratch
-- will yield new UUIDs, but since system accounts are queried by code + shard_id,
-- random UUIDs do not affect correctness.

DO $$
DECLARE
    shard INT;
    now_ts TIMESTAMPTZ := CURRENT_TIMESTAMP;
BEGIN
    FOR shard IN 0..9 LOOP
        INSERT INTO ledger_accounts (id, owner_id, type, code, shard_id, currency, balance, version, created_at, updated_at)
        VALUES 
            (gen_random_uuid(), NULL, 'CLEARING', 'ESCROW', shard, 'BDT', 0, 0, now_ts, now_ts),
            (gen_random_uuid(), NULL, 'REVENUE', 'PLATFORM_REVENUE', shard, 'BDT', 0, 0, now_ts, now_ts),
            (gen_random_uuid(), NULL, 'LIABILITY', 'VENDOR_PAYABLE', shard, 'BDT', 0, 0, now_ts, now_ts);
    END LOOP;
END $$;
