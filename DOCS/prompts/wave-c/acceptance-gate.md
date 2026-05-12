# Phase 1 Wave C — Acceptance gate (sub-prompt 12)

> **Branch:** runs on `main` after both `phase-1/adapter-bkash` and `phase-1/adapter-sslcommerz` have been merged via the merge train. No code changes other than the regenerated `DOCS/contracts/openapi.json` if the diff is non-empty.
> **Scope:** verify the two new adapters resolve through `PaymentProviderRegistry`, the isolation invariant holds across all real adapters on the classpath, the contract suites pass end-to-end, and the OpenAPI spec reflects the extended `Vendor` enum.
> **Read first:** [Wave C index](../PHASE_1_WAVE_C_PROMPTS.md); the merged commits from sub-prompts 0, 10, 11; `conflux-adapters/src/test/.../support/PaymentProviderRegistryIntegrationTest.java`.

---

## Prompt 12 — Wave C acceptance gate

```
You are running the Wave C acceptance gate on `main`, after sub-prompts 0, 10, and 11 have all merged. Your job is verification, OpenAPI regeneration, and a Wave C report. You do NOT add new adapter logic. You may fix gate-blocking issues in-place (single hotfix commit per issue, clearly labeled) only if they are surfaced by the gate itself.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_C_PROMPTS.md (full)
- The three Wave C merge commits on `main`
- conflux-adapters/src/test/.../support/PaymentProviderRegistryIntegrationTest.java (Wave A baseline — the gate extends it)
- DOCS/contracts/openapi.json on `main`

WORK ONLY IN
- conflux-adapters/src/test/.../wavec/* (new test package for gate-only assertions)
- DOCS/contracts/openapi.json (regenerated)
- PHASE_1_WAVE_C_REPORT.md (new, repo root)
- application.yml and application-test.yml ONLY if a gate-blocking config issue is found (rare; document in commit message)

DO NOT TOUCH
- Any adapter implementation. Failures in an adapter mean STOP and report — re-open the relevant sub-prompt.
- Any locked port or interface.
- Any Flyway migration.

GATE STEPS — execute in order, stop at the first failure

1. **Registry resolution** — write `PaymentProviderRegistryWaveCTest`:
   - `lookup(Vendor.BKASH)` returns an instance of `BkashAdapter`.
   - `lookup(Vendor.SSLCOMMERZ)` returns an instance of `SslcommerzAdapter`.
   - `lookup(Vendor.MOCK)` returns the existing `MockAdapter` (regression guard).
   - For every Vendor enum value NOT in {BKASH, SSLCOMMERZ, MOCK} (i.e. NAGAD/ROCKET/UPAY/PATHAO/MCASH/STRIPE), `lookup(v)` throws the "no adapter found" exception the registry already throws — these are Wave D scope. Assert the exception type matches the existing Wave A registry behavior.

2. **Adapter isolation (extended)** — write `WaveCAdapterIsolationTest`. This is the canonical home for cross-vendor concurrency tests; the per-adapter sub-prompts intentionally do NOT ship these.
   - Start three WireMock stub servers: one per adapter (Bkash on `/tokenized/checkout/create`, SSLCommerz on `/gwprocess/v4/api.php`, Mock is in-process).
   - Case A: configure the SSLCommerz stub with fixed-delay 8s; configure the Bkash stub with no delay. Fire three `initiate(...)` calls concurrently from three threads. Assert Bkash + Mock complete in < 1s wall-clock each; SSLCommerz call is still in-flight or has just returned `VENDOR_DOWN` / `MFS_ADAPTER_FAILURE` — but never blocks the other two.
   - Case B: configure the Bkash stub with fixed-delay 8s; configure SSLCommerz with no delay. Same assertions with roles swapped.
   - This is the "vendor isolation invariant" guard. If it fails, the offending adapter is sharing a thread pool or connection pool — STOP and re-open the relevant sub-prompt.

3. **Contract suite re-run** — `mvn -pl conflux-adapters -am verify` must be green. The BkashAdapterContractTest + SslcommerzAdapterContractTest from sub-prompts 10/11 run as part of this.

4. **Full reactor verify** — `mvn -pl conflux-application -am verify`. Spring Modulith + ArchUnit + JaCoCo aggregate must be green. The aggregate JaCoCo line ≥ 80%, branch ≥ 70%.

5. **Static analysis** — run the full CI parity locally:
   ```
   mvn spotless:check
   mvn -DskipTests compile spotbugs:spotbugs spotbugs:check pmd:pmd pmd:check
   mvn -DskipTests test-compile
   ```
   All four must be clean. If any are not, this gate is FAILED and the responsible sub-prompt is re-opened.

6. **gitleaks + log-leak audit** — two sub-steps:
   - 6a. `gitleaks detect --source . --no-banner -v`. Must be clean. Pay special attention to:
     - Any test fixture containing a real-looking `app_secret`, `store_passwd`, `id_token`, or `bank_tran_id` value. Replace with obvious dummies (`"app-secret-FIXTURE-NOT-REAL"`).
     - Any commit message body that pasted a request/response trace with real credentials.
   - 6b. **Log-leak grep on full test suite output.** Run `mvn -pl conflux-adapters,conflux-application -am verify -Dlogback.configurationFile=...` with logback's root level set to DEBUG, capturing stdout + stderr to `target/wave-c-test-logs.txt`. Then grep the captured logs for: `app_secret`, `app-secret`, `store_passwd`, `store-passwd`, `id_token`, `Bearer\s+\S{20,}`, alphanumeric session-key-like strings (length > 16). **Zero matches required** for the first four (literal secret tokens) and any match for the regex/length heuristics must be inspected manually and either MASKED or proven safe (e.g., a documented test fixture name). This is the safety net for the per-adapter prompts' "secret-redaction rules apply to logs" rule.

7. **OpenAPI regen + diff** — `mvn -Popenapi -pl conflux-application integration-test -DskipTests`. Copy `target/openapi/openapi.json` to `DOCS/contracts/openapi.json`. **Diff semantics:** ignore `info.version`, `info.title` timestamps, and component-property ordering shuffles (springdoc may reorder). Assert only that:
   - The `Vendor` enum value list inside `VendorConfigDto.vendor` (or wherever it surfaces) gained `SSLCOMMERZ`.
   - No path, operation, request-body, or response-body diff exists vs `main`.

   If anything else changed, an adapter or Wave B/A surface leaked into the spec — STOP and investigate.

8. **Wave C report** — write `PHASE_1_WAVE_C_REPORT.md` at repo root, modeled on `PHASE_1_WAVE_B_REPORT.md`:
   - One-paragraph summary: enum extension + two new adapters shipped, isolation invariant holds.
   - Locked-contract change log: exactly one entry — `Vendor` enum extended with `SSLCOMMERZ` (alphabetical position), `vendor_configs.vendor` CHECK extended via V1016.
   - Per-adapter JaCoCo numbers (line + branch) and contract-test counts.
   - **Follow-up: Bkash Execute step.** `BkashAdapter.confirm(paymentID, creds)` is shipped as a public adapter method but is NOT yet wired into `payment-core`'s redirect-callback path (Wave B sub-prompt 8b assumed synchronous capture in `initiate`). File this as a known cross-cutting follow-up; reference the file + line where `confirm` is defined. The follow-up requires a small `payment-core` patch to detect `Vendor.BKASH` on callback receipt and invoke `BkashAdapter.confirm` before marking the transaction COMPLETED.
   - Wave D readiness checklist: list of remaining real vendors (NAGAD, ROCKET, UPAY, PATHAO, MCASH, STRIPE), each with a one-line note on auth flavor (token vs per-request) so the Wave D pre-prompt can fan them out.
   - Known issues: anything else that surfaced but was triaged-not-fixed (link to issue or commit).

ACCEPTANCE CRITERIA
- All 7 gate steps pass.
- Single gate commit: `chore(wave-c): acceptance gate — registry + isolation + spec regen`. The regenerated `DOCS/contracts/openapi.json` and `PHASE_1_WAVE_C_REPORT.md` are part of this commit.
- If any step required a hotfix to an adapter sub-prompt's code, that hotfix lives in its own clearly-labeled commit BEFORE the gate commit, and the adapter sub-prompt's owner is the commit author. The gate commit is the orchestrator's.

FORBIDDEN
- Implementing or extending any adapter.
- Editing any locked port or interface.
- Adding any Flyway migration.
- Skipping a gate step.
- Suppressing a SpotBugs / PMD / Spotless / gitleaks finding to make the gate pass — fix it or re-open the responsible sub-prompt.

Output: gate-step status table (1–7 each PASS/FAIL with one-line evidence), JaCoCo aggregate, OpenAPI diff summary, link to `PHASE_1_WAVE_C_REPORT.md`.
```
