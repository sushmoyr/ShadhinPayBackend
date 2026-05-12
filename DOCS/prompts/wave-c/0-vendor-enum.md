# Phase 1 Wave C — Sub-prompt 0: `Vendor` enum extension + V1016 migration

> **Branch:** `phase-1/wave-c-vendor-enum` — single sub-prompt, **must merge to `main` before sub-prompts `10` and `11` start.**
> **Scope:** add `SSLCOMMERZ` to the locked `Vendor` enum, extend the V1012 CHECK constraint via a new V1016 migration, and add the SSLCommerz `application.yml` block. **No adapter implementation. No Java change to `PartnerCredentialsConfig`** — it's a `Map<String, Map<String, String>>` that absorbs the new block via Spring relaxed binding.
> **Read first:** [Wave C index](../PHASE_1_WAVE_C_PROMPTS.md) — cross-cutting decisions; `conflux-adapters/CLAUDE.md`; `PHASE_1_WAVE_A_REPORT.md` § "Locked Wave A Contracts"; `PHASE_1_WAVE_B_REPORT.md` § locked contracts.

---

## Prompt 0 — vendor enum + provisioning CHECK + partner credentials config

```
You are running the ONLY sequential sub-prompt of Wave C on branch `phase-1/wave-c-vendor-enum`. Wave B is on `main`. Your job is to extend three locked-but-extensible surfaces so the two parallel adapter agents (sub-prompts 10 + 11) can run without colliding. You implement NO adapter logic. You add NO `PaymentProvider` impl. Sub-prompts 10 and 11 do that.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_C_PROMPTS.md (full — especially Cross-cutting decisions #1, #5)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/Vendor.java
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/config/ResilienceConfig.java
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/support/PaymentProviderRegistryIntegrationTest.java (already enum-agnostic — see deliverable 6)
- conflux-provisioning/src/main/resources/db/migration/V1012__provisioning_schema.sql
- conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/config/PartnerCredentialsConfig.java — READ ONLY (do not edit; it's `Map<String, Map<String, String>>` and absorbs the new vendor block automatically)
- conflux-application/src/main/resources/application.yml — the `conflux.adapters.partner-credentials.*` block

WORK ONLY IN
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/Vendor.java
- conflux-provisioning/src/main/resources/db/migration/V1016__vendor_configs_extend_vendor_check.sql (new)
- conflux-application/src/main/resources/application.yml — add the `sslcommerz:` sub-block under `conflux.adapters.partner-credentials.*`, env-var-driven, sandbox URL default
- conflux-application/src/main/resources/application-test.yml — same, blank/test-fixture values
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/port/VendorTest.java (new or existing — verify presence first)
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/config/ResilienceConfigTest.java (new or existing)
- conflux-provisioning/src/test/java/pay/conflux/backend/provisioning/migration/V1016MigrationTest.java (new — Testcontainers Postgres)

DO NOT TOUCH
- Any `PaymentProvider` impl, including MockAdapter.
- conflux-payment-core/.
- The `PaymentProvider`, `VendorAuthClient`, `TokenService`, `VendorCredentials`, `VendorResponse`, `VendorStatus` interfaces/records.
- The `ApiResult<T>` envelope, `ErrorCode` enum.
- **`PartnerCredentialsConfig.java`** — Wave B shipped it as a generic `Map<String, Map<String, String>>`. Adding a YAML block under `conflux.adapters.partner-credentials.sslcommerz.*` is sufficient; Spring relaxed binding picks it up at boot. No Java change.
- Any existing Wave A or Wave B migration.

DELIVERABLES

1. `Vendor.java` — add `SSLCOMMERZ` between `STRIPE` and `MOCK` (alphabetical insertion: ... PATHAO, MCASH, SSLCOMMERZ, STRIPE, MOCK). Update the Javadoc to note SSLCommerz is a Bangladeshi card aggregator (no MFS wallet), distinct from BKASH/NAGAD/ROCKET which are MFS wallets.

2. `V1016__vendor_configs_extend_vendor_check.sql`:
   ```sql
   -- Wave C sub-prompt 0: extend vendor_configs.vendor CHECK constraint to include SSLCOMMERZ.
   -- V1012 created the CHECK as an inline (unnamed) column constraint; PostgreSQL auto-named
   -- it but the exact name varies by PG version. Look up the actual name from pg_constraint
   -- and drop it dynamically, then recreate with the extended value list.

   DO $$
   DECLARE
       cname text;
   BEGIN
       SELECT conname INTO cname
       FROM pg_constraint
       WHERE conrelid = 'vendor_configs'::regclass
         AND contype = 'c'
         AND pg_get_constraintdef(oid) LIKE '%vendor%IN%';
       IF cname IS NOT NULL THEN
           EXECUTE format('ALTER TABLE vendor_configs DROP CONSTRAINT %I', cname);
       END IF;
   END $$;

   ALTER TABLE vendor_configs ADD CONSTRAINT vendor_configs_vendor_check
       CHECK (vendor IN ('BKASH','NAGAD','ROCKET','UPAY','PATHAO','MCASH','SSLCOMMERZ','STRIPE','MOCK'));
   ```
   The DO block is name-agnostic — it finds the CHECK constraint by table + column-pattern match and drops whatever name PostgreSQL assigned. The re-added constraint is explicitly named `vendor_configs_vendor_check` so future migrations can target it by name.

3. **No Java change to `PartnerCredentialsConfig`.** The Wave B implementation is a `Map<String, Map<String, String>>` keyed by lowercase vendor name; adding the `sslcommerz` block to `application.yml` (deliverable 4) is sufficient. **DO NOT** add a typed `SslcommerzCredentials` record — that would require a refactor of the existing Wave B binding and is out of Wave C scope.

4. `application.yml` — add under `conflux.adapters.partner-credentials:` (mirror the shape Wave B used for `bkash`):
   ```yaml
       sslcommerz:
         store-id: ${SSLCOMMERZ_STORE_ID:}
         store-passwd: ${SSLCOMMERZ_STORE_PASSWD:}
         base-url: ${SSLCOMMERZ_BASE_URL:https://sandbox.sslcommerz.com}
   ```
   The `application-test.yml` `sslcommerz` block uses blank strings + the sandbox URL so the config binder doesn't fail in test profile. (After Spring relaxed-binding, adapters look up `creds.get("storeId")`, `creds.get("storePasswd")`, `creds.get("baseUrl")` — camelCase, not hyphenated.)

5. **Verify the `ResilienceConfig` registry seeds a circuit breaker for `SSLCOMMERZ` automatically** (it loops `Vendor.values()` — no code change needed). Add an explicit `@Test` to `conflux-adapters/src/test/.../config/ResilienceConfigTest.java` asserting `registry.find("SSLCOMMERZ").isPresent()`. If `ResilienceConfigTest` doesn't exist, create it with this single test.

6. **Registry test is already enum-agnostic by construction** — `PaymentProviderRegistryIntegrationTest` uses `@SpringBootTest(classes = {PaymentProviderRegistry.class, MockAdapter.class})` (explicit bean list), so adding a new `Vendor` value does NOT change what beans Spring loads. No change required for sub-prompt 0. If the agent finds otherwise (e.g., the test was rewritten since this prompt was authored), record the actual outcome in the commit message body (one line: `registry test already enum-agnostic — no change` or `registry test fixed: <one-line diff>`).

TESTS (≥ 1 unit test per new artifact; no per-module coverage gate drop)

- `VendorTest` (or extension of existing) — assert `Vendor.valueOf("SSLCOMMERZ")` returns the new value; assert `Vendor.values().length == 9` (one more than the Wave A baseline of 8). The hardcoded count is intentional — it forces the next wave's pre-prompt to update it deliberately rather than silently absorbing new values.
- `ResilienceConfigTest` — as above. Assert `registry.find("SSLCOMMERZ").isPresent()`.
- `PartnerCredentialsConfigBindingTest` — Spring Boot `@SpringBootTest(classes = PartnerCredentialsConfig.class)` with `@TestPropertySource` setting `conflux.adapters.partner-credentials.sslcommerz.store-id=foo`, `...store-passwd=bar`, `...base-url=https://example`. Assert `config.credentialsFor("sslcommerz")` returns a map with `storeId=foo`, `storePasswd=bar`, `baseUrl=https://example` (camelCase keys after Spring relaxed binding).
- `V1016MigrationTest` (Testcontainers Postgres in conflux-provisioning) — apply V1001..V1016, insert a row with `vendor='SSLCOMMERZ'` into `vendor_configs`, assert it succeeds; insert one with `vendor='NOPE'`, assert it fails with a constraint violation. Also assert the constraint is now named `vendor_configs_vendor_check` (queryable via `pg_constraint`).

ACCEPTANCE CRITERIA
- `mvn -pl conflux-adapters,conflux-provisioning -am verify` BUILD SUCCESS.
- `mvn -pl conflux-application -am verify` BUILD SUCCESS (no Wave A or Wave B test regressions).
- JaCoCo: no per-module gate drop.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Single commit: `feat(adapters,provisioning): extend Vendor enum + V1016 CHECK for SSLCOMMERZ (wave-c sub-prompt 0)`.

FORBIDDEN
- Implementing `SslcommerzAdapter`, `SslcommerzAuthClient`, or any sub-package under `pay.conflux.backend.adapters.sslcommerz.*`.
- Implementing `BkashAdapter`.
- Touching `conflux-payment-core`.
- Adding any new `ErrorCode` value.
- Editing V1012 or any prior migration.
- Adding a root-pom dependency.
- Adding any seed data for `vendor_configs` — the new row insert in the V1016 test is rolled back.

Output: file tree, migration SQL diff, the assertion deltas in the two test files, JaCoCo tail.
```
