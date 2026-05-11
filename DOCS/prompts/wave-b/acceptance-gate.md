# Phase 1 Wave B — acceptance gate

> **When:** after both module branches (`phase-1/provisioning`, `phase-1/payment-core`) are merged to `main` via the merge train.
> **Output:** `PHASE_1_WAVE_B_REPORT.md` at the repo root.
> **Read first:** the [Wave B index](../PHASE_1_WAVE_B_PROMPTS.md), `DEVELOPMENT_WORKFLOW.md` §4.4 + §7.2 + §8, `PHASE_1_WAVE_A_REPORT.md` (the format you are mirroring).

---

## Prompt 9 — Wave B acceptance gate

```
You are the orchestrator agent for Phase 1 Wave B acceptance. All 6 module sub-prompts (7a–8c) are merged to `main`. Your job: verify the Wave B definition of done, regenerate `DOCS/contracts/openapi.json` (this is the merge train's responsibility per cross-cutting decision #2), and produce `PHASE_1_WAVE_B_REPORT.md`. You write no production code.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §4.4 (wave coordination), §7.2 (definition of done), §8 (CI gates)
- DOCS/prompts/PHASE_1_WAVE_B_PROMPTS.md (the index) and DOCS/prompts/wave-b/*.md (all module sub-prompts)
- PHASE_1_WAVE_A_REPORT.md (the format you are mirroring; also the source of locked Wave A contracts)

CHECKS TO RUN

For each item below, run the listed command and record PASS/FAIL with evidence (short command output snippet).

1. **All 6 sub-prompt commits land on `main`**
   - `git log --oneline main --since=<wave-B-start-date>` shows commits matching: `(provisioning): schema`, `(provisioning): cross-module impls`, `(provisioning): controllers`, `(payment-core): schema`, `(payment-core): vendor-callback`, `(payment-core): concurrency`. (Squashing into one feat commit per module is acceptable per Wave A precedent — just verify the deliverables in the tree.)

2. **Whole repo builds**
   - `mvn -q clean verify` exit 0.

3. **Per-module 80% JaCoCo**
   - `mvn -Pcoverage verify`: every Wave A + Wave B module ≥ 80% line. Pay special attention to `provisioning` and `payment-core` — these are the new modules.

4. **`ApplicationModules.verify()` green**
   - `mvn -pl conflux-application -am test -Dtest=ModularityTests`. Verify the regenerated `target/spring-modulith-docs/` shows:
     - `provisioning` module owns the two cross-module impls (`GetBusinessByApiKeyUseCaseImpl`, `GetVendorConfigUseCaseImpl`, `CredentialsResolverImpl`) and the two event listeners (`MerchantVerifiedEventListener`, `UserBlockedEventListener`).
     - `payment-core` module owns `InitiatePaymentUseCaseImpl`, `ProcessVendorCallbackUseCaseImpl`, `RefundPaymentUseCaseImpl`, `WebhookOutboxDispatcher`, `ReconciliationScheduler`.
     - No leakage: no module imports another module's `entity`, `repository`, `mapper`, or `usecase.impl` packages.

5. **ArchUnit suite green** (no new violations)
   - `mvn -pl conflux-application -am test -Dtest=ArchitectureRulesTest`. Should include new rules covering provisioning + payment-core (or the existing slice-rules should pass for them).

6. **Spotless + PMD + gitleaks clean**

7. **Cross-module wiring works end-to-end** — a single `@SpringBootTest` exercises the full payment-initiation pipeline:
   - Register merchant (identity) → verify (identity admin) → modulith event triggers `MerchantVerifiedEventListener` → default business + API key auto-exist (provisioning) → call `POST /api/v1/payments` with the API key → orchestrator runs (payment-core) → MockAdapter returns success → ledger journal entry recorded → quota usage incremented → webhook outbox row enqueued → dispatcher delivers to a WireMock endpoint with valid HMAC signature.
   - This is the **golden-path integration test**. If it doesn't exist as a single test, the agent writes it as part of this check (the only production-code-adjacent write allowed in this prompt — it's a test, not production code).

8. **Idempotency 100-thread concurrency** — re-run `PaymentInitiationConcurrencyIT`. 100 threads, same idempotency key → exactly 1 `Transaction`. PASS criterion is the explicit `assertThat(transactionCount).isEqualTo(1)` in the test.

9. **Modulith event flow & replay**
   - Re-run `PaymentCoreModulithReplayIT` from 8c. Verify the `event_publication` table shows redelivery after a simulated downstream-listener crash.

10. **`PENDING_RECOVERY` resolution** — manually simulate a `PENDING_RECOVERY` transaction (insert via repository in a test fixture). Verify the `ReconciliationScheduler` resolves it to `COMPLETED` after `MockAdapter.queryStatus` returns a definitive answer. Verify the 24h-timeout path finalizes a stuck `PENDING_RECOVERY` as `FAILED`.

11. **OpenAPI regenerated and committed**
    - Boot the application context, dump the OpenAPI JSON, write to `DOCS/contracts/openapi.json`. Diff against the Wave A baseline:
      - `ApiResult<T>` envelope unchanged.
      - `ApiKeyAuth` security scheme unchanged.
      - New endpoints listed (Wave B should add ~10–12 paths: merchant businesses/vendors/api-keys/webhook, admin businesses, public payments + refund + callback). Total path count should be Wave A baseline (27) + Wave B additions.
    - Commit: `docs: regenerate openapi.json after Wave B merge`.

12. **Public-surface security audit**
    - Every endpoint under `/api/v1/merchant/**` requires `MERCHANT` authority (via the API-key filter).
    - Every endpoint under `/api/v1/admin/**` requires `ADMIN_*` authority.
    - The vendor-callback endpoint `/api/v1/payments/callback/{vendor}` is the ONLY whitelisted non-`/actuator/**` endpoint. No JWT or API key bypass on any other route.
    - Grep test: `grep -r "permitAll" conflux-application/src/main/java/` should match only the callback + actuator + api-docs whitelist.

13. **Webhook secret hygiene**
    - Scan all log output from the integration tests: no webhook secret appears in any log line. (Capture via test logback config.)

14. **Latency sanity**
    - `GetBusinessByApiKeyUseCaseImpl` p95 < 50 ms with warm Redis cache (the 7b microbenchmark — re-verify).
    - `InitiatePaymentUseCaseImpl` end-to-end p95 < 500 ms with MockAdapter (best-effort — log actual numbers; real targets land in Phase 2 when real adapters introduce vendor latency).

15. **No secrets committed**
    - `gitleaks detect --source .` zero findings.

REPORT FORMAT

Produce `PHASE_1_WAVE_B_REPORT.md` at the repo root containing:
- A table of every check above with PASS/FAIL and evidence.
- A "Blockers" section listing every FAIL and the sub-prompt number that should fix it.
- A "Wave C readiness" section: if every check is PASS, state "Wave A + B complete; Wave C (real-vendor adapters: bKash, Nagad, Stripe, Rocket, Upay, Pathao, mCash) may start. Frontend integration may also start in parallel — the public API surface is now stable."
- A "Frontend integration readiness" subsection listing:
  - The stable public API endpoints frontend may consume now.
  - The auth model (API key in `Authorization: Bearer sp_*` or `X-API-Key`).
  - The webhook-receiver requirements merchants must implement (HTTPS, HMAC-SHA256 verification of `X-PGW-Signature`).
  - The `MockAdapter` `mock_outcome` metadata key for frontend developers to test success/fail/cancel paths against a non-production vendor.
- A "Locked Wave B contracts" section listing every public type Wave C agents will treat as read-only:
  - The two `*Routes` classes (`ProvisioningRoutes`, `PaymentCoreRoutes`).
  - The orchestration order in `InitiatePaymentUseCaseImpl` — Wave C must not change it.
  - The `WebhookOutbox` schema and the dispatcher's HMAC signing convention.
  - The `ReconciliationScheduler`'s `PENDING_RECOVERY` resolution rules (only path that finalizes RECOVERY → FAILED is the 24h timeout).
  - The `CredentialsResolver` interface — Wave C real-vendor adapters consume this to get plaintext credentials at dispatch time.

FORBIDDEN
- Modifying any code beyond regenerating `DOCS/contracts/openapi.json` and (if missing) writing the golden-path integration test from check 7.
- Marking a check PASS without command output as evidence.
- Promoting Wave B as done if any module's JaCoCo gate is below 80%.
- Promoting Wave B as done if the idempotency concurrency test is flaky (one failure in 5 runs is a failure; the invariant must be unconditional).

Output: contents of `PHASE_1_WAVE_B_REPORT.md`.
```
