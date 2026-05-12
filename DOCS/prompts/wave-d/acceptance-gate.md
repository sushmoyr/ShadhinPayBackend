# Phase 1 Wave D — Acceptance gate

> **Branch:** runs on `main` after all Wave D branches have merged via the merge train:
> - `phase-1/wave-d-admin-auth` (Track 1: 1a → 1b → 1c)
> - `phase-1/wave-d-bkash-execute` (Bkash Execute wiring)
> - `phase-1/adapter-nagad`, `phase-1/adapter-rocket`, `phase-1/adapter-upay`, `phase-1/adapter-pathao`, `phase-1/adapter-mcash`, `phase-1/adapter-stripe` (Track 2: one per vendor)
>
> **Scope:** verify the admin auth surface is reachable, every new adapter resolves through `PaymentProviderRegistry`, the super-admin bootstrap fails fast on a fresh container, the Bkash Execute step is wired, and the regenerated `DOCS/contracts/openapi.json` reflects the additive admin endpoints from Track 1 (and nothing else).
>
> **Read first:** [Wave D index](../PHASE_1_WAVE_D_PROMPTS.md); the merged commits from Wave D's eight branches; `DOCS/prompts/wave-c/acceptance-gate.md` (canonical gate template — Wave D extends its shape).

---

## Prompt — Wave D acceptance gate

```
You are running the Wave D acceptance gate on `main`, after all eight Wave D
branches have merged. Your job is verification, OpenAPI regeneration, and a
Wave D report. You do NOT add new feature logic. You may fix gate-blocking
issues in-place (single hotfix commit per issue, clearly labeled) only if they
are surfaced by the gate itself, and only on the responsible sub-prompt's
owning module.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (full)
- DOCS/prompts/wave-c/acceptance-gate.md (canonical gate template)
- All Wave D merge commits on `main`
- conflux-adapters/src/test/.../support/PaymentProviderRegistryIntegrationTest.java (Wave A baseline) and the Wave C extension `PaymentProviderRegistryWaveCTest`
- DOCS/contracts/openapi.json on `main` (the Wave C-extended version is the starting baseline)

WORK ONLY IN
- conflux-adapters/src/test/.../waved/* (new test package for gate-only assertions)
- conflux-application/src/test/.../waved/* (gate-only integration tests that span modules)
- DOCS/contracts/openapi.json (regenerated)
- PHASE_1_WAVE_D_REPORT.md (new, repo root)
- application.yml / application-test.yml ONLY if a gate-blocking config issue is found (rare; document in commit message)

DO NOT TOUCH
- Any adapter implementation. Failures in an adapter mean STOP and report — re-open the relevant adapter sub-prompt.
- `AdminProfile`, `User`, `JwtAuthorizationFilter`, `SuperAdminBootstrap`, or any Wave D Track 1 production code. Failures there mean STOP and re-open Track 1.
- Any locked port, interface, or event record.
- Any Flyway migration.

GATE STEPS — execute in order, stop at the first failure.

1. **Registry resolution (Wave D extension).** Write `PaymentProviderRegistryWaveDTest`:
   - `lookup(Vendor.NAGAD) instanceof NagadAdapter`
   - `lookup(Vendor.ROCKET) instanceof RocketAdapter`
   - `lookup(Vendor.UPAY) instanceof UpayAdapter`
   - `lookup(Vendor.PATHAO) instanceof PathaoAdapter`
   - `lookup(Vendor.MCASH) instanceof McashAdapter`
   - `lookup(Vendor.STRIPE) instanceof StripeAdapter`
   - Plus regression-guard the existing Wave C resolutions (`BKASH → BkashAdapter`, `SSLCOMMERZ → SslcommerzAdapter`, `MOCK → MockAdapter`).
   - At this point every Vendor enum value has an adapter — there should be NO "no adapter found" exception for any value. Assert this explicitly.

2. **Cross-adapter isolation (extended).** Write `WaveDAdapterIsolationTest`. Extends the Wave C precedent (`WaveCAdapterIsolationTest`) to cover the six new adapters.
   - Start 6+ WireMock stub servers, one per token-based adapter. For per-request adapters (ROCKET, MCASH, STRIPE if hand-rolled), stub the initiate endpoint directly.
   - For each pair (slow-adapter, other-adapter) — running all 21 pairs would be excessive; instead pick one representative slow case per auth flavor:
     - **Token-based slow:** delay NAGAD's initiate by 8s; assert ROCKET + MOCK both complete < 1s and never block.
     - **Per-request slow:** delay ROCKET's initiate by 8s; assert NAGAD + MOCK both complete < 1s.
     - **OAuth2 slow:** delay PATHAO's initiate by 8s; assert STRIPE + MOCK both complete < 1s.
   - This is the "vendor isolation invariant" guard. If it fails, an adapter is sharing a thread pool, connection pool, or token cache — STOP and re-open the responsible sub-prompt.

3. **Admin authority matrix (cross-module).** Re-run `AdminAuthorityMatrixIT` from Track 1 sub-prompt 1c. This test asserts the full {MERCHANT, VIEWER, MANAGER, SUPER} × {7 admin endpoints} matrix. The gate re-runs it on `main` to catch any regression introduced by Track 2 adapter merges (none expected, but verification is cheap).

4. **Super-admin bootstrap on a fresh container.** Write `SuperAdminBootstrapGateIT`:
   - Spin up a fresh Testcontainers Postgres + Redis with NO `SUPER_ADMIN_*` env vars set. Boot the application. Assert the context refuses to start with the documented `IllegalStateException`.
   - Repeat with `SUPER_ADMIN_IDENTIFIER=admin@conflux.test` + `SUPER_ADMIN_PASSWORD=…`. Assert the context starts, and exactly one SUPER admin row exists with the configured identifier.
   - Verify the BCrypt hash verifies against the configured password (do this via the `BCryptPasswordEncoder` bean, not by hashing the plaintext yourself — that would be a weaker test).
   - If any of these fail, STOP and re-open Track 1 1b.

5. **Filter coexistence (cross-module).** Re-run `FilterCoexistenceIT` from Track 1 sub-prompt 1c. Asserts the JWT filter and API-key filter remain mutually exclusive per request, and merchant API-key auth (Wave B 8c) is not regressed by JWT filter introduction.

6. **Bkash Execute wiring.** Re-run the extended Bkash WireMock callback IT from `bkash-execute-wiring.md`. Asserts that on a Bkash callback, `BkashAdapter.confirm` is invoked; on an SSLCommerz (or any non-Bkash) callback, it is NOT invoked. If this fails, STOP and re-open the bkash-execute-wiring sub-prompt.

7. **Contract suite re-run.** `mvn -pl conflux-adapters -am verify` must be green. The six new adapter contract tests run as part of this.

8. **Full reactor verify.** `mvn -pl conflux-application -am verify`. Spring Modulith + ArchUnit + JaCoCo aggregate must be green. Aggregate JaCoCo line ≥ 80%, branch ≥ 70%.

9. **Static analysis (CI parity).**
   ```
   mvn spotless:check
   mvn -DskipTests compile spotbugs:spotbugs spotbugs:check pmd:pmd pmd:check
   mvn -DskipTests test-compile
   ```
   All four must be clean.

10. **gitleaks + log-leak audit (Wave C 6a + 6b pattern).** Two sub-steps:
    - **10a.** `gitleaks detect --source . --no-banner -v`. Must be clean. Pay special attention to:
      - The `application.yml` blocks for the six new vendors — every secret slot must be `${ENV_VAR:}` or have an obvious dummy default.
      - `SuperAdminBootstrap` test fixtures — the test password must be a literal fixture like `"FIXTURE-NOT-REAL"` and the BCrypt hash in any seed SQL must be commented as `<!-- fixture; not a real credential -->`.
      - The JWT test secret (Base64-encoded test fixture in `application-test.yml`) — confirm it's flagged as fixture-only.
      - Any commit message body that pasted a request/response trace with real credentials.
    - **10b.** Log-leak grep on the full DEBUG-level test output. Run the reactor verify with `--logback.configurationFile=...` set to root level DEBUG, capturing stdout + stderr to `target/wave-d-test-logs.txt`. Grep for: `app_secret`, `app-secret`, `store_passwd`, `store-passwd`, `id_token`, `Bearer\s+\S{20,}`, `SUPER_ADMIN_PASSWORD`, `JWT_SECRET`, alphanumeric session-key-like strings (length > 16). **Zero matches required** for the literal secret tokens; regex/length-heuristic hits must each be inspected and either MASKED or proven safe.

11. **OpenAPI regen + diff.** `mvn -Popenapi -pl conflux-application integration-test -DskipTests`. Copy `target/openapi/openapi.json` to `DOCS/contracts/openapi.json`. **Diff semantics:** ignore `info.version`, `info.title` timestamps, and component-property ordering shuffles. Assert that:
    - The `/api/v1/admin/admins`, `/api/v1/admin/admins/{id}/tier`, `/api/v1/admin/admins/{id}/disable`, and `/api/v1/admin/me` paths exist in the spec.
    - No path, operation, request-body, or response-body diff exists outside the Track 1 admin endpoints.
    - The `Vendor` enum value list in `VendorConfigDto` (or wherever it surfaces) is unchanged from `main` baseline — Wave D Track 2 added no new enum values.
    - If anything else changed, STOP and investigate.

12. **Wave D report.** Write `PHASE_1_WAVE_D_REPORT.md` at repo root, modeled on `PHASE_1_WAVE_C_REPORT.md`:
    - One-paragraph summary: admin surface unlocked, all six remaining adapters shipped, Bkash Execute wired.
    - Locked-contract change log: ONE entry — `AdminProfile.adminTier` column added via V1017. No changes to `Vendor`, `PaymentProvider`, `ErrorCode`, `ApiResult`, `AuthenticatedPrincipal`, or any event record.
    - Per-adapter JaCoCo numbers (line + branch) and contract-test counts (6 new adapters).
    - Authority matrix snapshot (the 4 × 7 table from `AdminAuthorityMatrixIT`).
    - **Phase 1 closeout checklist:** all real vendors live, admin surface reachable, super-admin seed enforced, Bkash Execute wired. Wave E (integration / E2E per `DEVELOPMENT_WORKFLOW.md §5`) is now unblocked.
    - Known issues: anything triaged-not-fixed (link to issue or commit).
    - Phase 2 readiness checklist: list of `DEVELOPMENT_WORKFLOW.md §5.1` scenarios that now have backing implementations (vs. those that need Wave E to write integration tests).

ACCEPTANCE CRITERIA
- All 12 gate steps pass.
- Single gate commit: `chore(wave-d): acceptance gate — admin auth + 6 adapters + Bkash wiring + spec regen`. The regenerated `DOCS/contracts/openapi.json` and `PHASE_1_WAVE_D_REPORT.md` are part of this commit.
- If any step required a hotfix to a Wave D sub-prompt's code, that hotfix lives in its own clearly-labeled commit BEFORE the gate commit, and the sub-prompt's owner is the commit author. The gate commit is the orchestrator's.

FORBIDDEN
- Implementing or extending any adapter.
- Editing any locked port, interface, or event record.
- Editing any Wave D Track 1 production code (the JWT filter, bootstrap, use cases).
- Adding any Flyway migration.
- Skipping a gate step.
- Suppressing a SpotBugs / PMD / Spotless / gitleaks finding to make the gate pass — fix it or re-open the responsible sub-prompt.

Output: gate-step status table (1–12 each PASS/FAIL with one-line evidence), JaCoCo aggregate, OpenAPI diff summary (only admin endpoints added), the AdminAuthorityMatrixIT 4×7 cell-status grid, link to `PHASE_1_WAVE_D_REPORT.md`.
```
