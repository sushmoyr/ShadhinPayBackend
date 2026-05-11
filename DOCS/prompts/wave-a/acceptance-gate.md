# Phase 1 Wave A — acceptance gate

> **When:** after all five module branches (`phase-1/identity`, `phase-1/ledger`, `phase-1/risk`, `phase-1/quota`, `phase-1/adapters`) are merged to `main` via the merge train.
> **Output:** `PHASE_1_WAVE_A_REPORT.md` at the repo root.
> **Read first:** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md), `DEVELOPMENT_WORKFLOW.md` §4.4 + §7.2 + §8, `PHASE_0_REPORT.md` (the format you are mirroring).

---

## Prompt 6 — Wave A acceptance gate

```
You are the orchestrator agent for Phase 1 Wave A acceptance. All 13 module sub-prompts (1a–5b) are merged to `main` via the merge train. Your job: verify the Wave A definition of done, regenerate `DOCS/contracts/openapi.json` (this is the merge train's responsibility per cross-cutting decision #2), and produce `PHASE_1_WAVE_A_REPORT.md`. You write no production code.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §4.4 (wave coordination), §7.2 (definition of done), §8 (CI gates)
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md (the index) and DOCS/prompts/wave-a/*.md (all module sub-prompts)
- PHASE_0_REPORT.md (the format you are mirroring)

CHECKS TO RUN

For each item below, run the listed command and record PASS/FAIL with evidence (short command output snippet).

1. **All 13 sub-prompt commits land on `main`**
   - `git log --oneline main --since=<wave-A-start-date>` shows commits matching: `(identity): foundation`, `(identity): KYC`, `(identity): MFA`, `(ledger): schema`, `(ledger): event listener`, `(ledger): controllers`, `(risk): persistence`, `(risk): SpEL engine`, `(risk): evaluate`, `(quota): Reserve`, `(quota): controllers`, `(adapters): MockAdapter`, `(adapters): Resilience4j`.

2. **Whole repo builds**
   - `mvn -q clean verify` exit 0.

3. **Per-module 80% JaCoCo**
   - `mvn -Pcoverage verify`: every Wave A module ≥ 80% line.

4. **`ApplicationModules.verify()` green**
   - `mvn -pl conflux-application -am test -Dtest=ModularityTests`. Verify the regenerated `target/spring-modulith-docs/` shows the new use-case impls inside their owning modules with no leakage.

5. **ArchUnit suite green** (no new violations)
   - `mvn -pl conflux-application -am test -Dtest=ArchitectureRulesTest`.

6. **Spotless + PMD + gitleaks clean**

7. **Cross-module use-case interfaces are implemented**
   - `RecordJournalEntryUseCaseImpl`, `GetAccountBalanceUseCaseImpl` (ledger).
   - `EvaluateTransactionUseCaseImpl` (risk).
   - `ReserveQuotaUseCaseImpl`, `ConfirmQuotaUseCaseImpl`, `ReleaseQuotaUseCaseImpl`, `GetUsageUseCaseImpl` (quota).
   - Confirm `provisioning.GetBusinessByApiKeyUseCase` / `GetVendorConfigUseCase` and `payment-core.InitiatePaymentUseCase` are STILL interface-only — that's correct for Wave A.

8. **`MockAdapter` registered with `PaymentProviderRegistry`**
   - `@SpringBootTest`: `PaymentProviderRegistry.lookup(Vendor.MOCK)` returns `MockAdapter`; `lookup(Vendor.BKASH)` throws `MfsAdapterException`.

9. **`NoopTokenService` deleted**
   - `git ls-files | grep -i noop` returns nothing.

10. **Modulith event flow end-to-end** — re-run the ledger event-listener integration test from 2b.

11. **OpenAPI regenerated and committed**
    - Boot the application context, dump the OpenAPI JSON, write to `DOCS/contracts/openapi.json`. Diff against Phase 0 baseline:
      - `ApiResult<T>` envelope unchanged.
      - `ApiKeyAuth` security scheme unchanged.
      - New endpoints listed (count > Phase 0 baseline).
    - Commit: `docs: regenerate openapi.json after Wave A merge`.

12. **Latency / concurrency sanity checks**
    - `risk` p99 < 50ms (the 3c benchmark; check its commit message for actual numbers).
    - `quota` 100-thread CountDownLatch test green.
    - `ledger` 100-thread concurrent posting test green.

13. **No secrets committed**
    - `gitleaks detect --source .` zero findings.

REPORT FORMAT

Produce `PHASE_1_WAVE_A_REPORT.md` at the repo root containing:
- A table of every check above with PASS/FAIL and evidence.
- A "Blockers" section listing every FAIL and the sub-prompt number that should fix it.
- A "Wave B readiness" section: if every check is PASS, state "Wave A complete; Wave B (provisioning, then payment-core) may start." If any FAIL, list the agent re-runs needed.
- A "Locked Wave A contracts" section listing every public type Wave B agents will treat as read-only:
  - All controllers' route constants (per-module Routes classes).
  - The four cross-module use-case impls' externally-visible behavior contracts (idempotency keys, fail-open/closed posture).
  - Any new error messages used in `ApiResult<T>` envelopes.
  - The TOTP-based MFA contract (secret returned once, encryption purpose).
  - The risk decision contract (BLOCK on engine failure).
  - The quota soft-reservation contract (reservation id, TTL).
  - The adapter `VendorAuthClient` interface (Wave C agents will implement this per vendor).

FORBIDDEN
- Modifying any code beyond regenerating `DOCS/contracts/openapi.json`.
- Marking a check PASS without command output as evidence.
- Promoting Wave A as done if any module's JaCoCo gate is below 80%.

Output: contents of `PHASE_1_WAVE_A_REPORT.md`.
```
