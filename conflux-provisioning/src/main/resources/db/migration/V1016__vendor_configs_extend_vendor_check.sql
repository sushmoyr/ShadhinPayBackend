-- Wave C sub-prompt 0: extend vendor_configs.vendor CHECK constraint to include SSLCOMMERZ.
-- V1012 created the CHECK as an inline (unnamed) column constraint; PostgreSQL auto-named
-- it but the exact name varies by PG version. Look up the actual name from pg_constraint
-- and drop it dynamically, then recreate with the extended value list under a stable name.

DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'vendor_configs'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%vendor%'
      AND pg_get_constraintdef(oid) ILIKE '%BKASH%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE vendor_configs DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE vendor_configs ADD CONSTRAINT vendor_configs_vendor_check
    CHECK (vendor IN ('BKASH','NAGAD','ROCKET','UPAY','PATHAO','MCASH','SSLCOMMERZ','STRIPE','MOCK'));
