-- Wave D Track 1 sub-prompt 1a: add admin_tier column to admin_profiles.
-- Existing rows default to VIEWER per DEVELOPMENT_WORKFLOW.md §4.4 guardrails —
-- a separate manual promotion is required to elevate them to MANAGER or SUPER.
ALTER TABLE admin_profiles
    ADD COLUMN admin_tier VARCHAR(16) NOT NULL DEFAULT 'VIEWER';
ALTER TABLE admin_profiles
    ADD CONSTRAINT admin_profiles_admin_tier_check
    CHECK (admin_tier IN ('VIEWER','MANAGER','SUPER'));
