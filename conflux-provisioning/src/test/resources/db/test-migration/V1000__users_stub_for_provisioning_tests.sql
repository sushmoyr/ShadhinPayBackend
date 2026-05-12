-- Test-only stub for the conflux-identity `users` table. The provisioning module's
-- repository integration test runs in isolation and does not include the real V1001
-- migration on its classpath. This file lives outside `db/migration` so it never
-- runs in production; it is loaded via a test-only Flyway location.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    status TEXT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false
);
