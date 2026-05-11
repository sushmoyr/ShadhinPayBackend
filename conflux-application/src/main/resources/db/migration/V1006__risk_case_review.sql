ALTER TABLE risk_evaluations
    ADD COLUMN IF NOT EXISTS reviewed_by_admin_id UUID NULL,
    ADD COLUMN IF NOT EXISTS review_decision VARCHAR(16) NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ NULL;
