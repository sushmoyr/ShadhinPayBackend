# Phase 1 Wave A — `adapters` module prompts (Wave A scope only)

> **Branch:** `phase-1/adapters` — run both sub-prompts sequentially in the same git worktree on the same branch.
> **Scope (Wave A only):** ship `MockAdapter`, replace `NoopTokenService` with a real Redis-backed `RedisTokenService`, wire Resilience4j circuit breakers per vendor, build a reusable WireMock test harness for Wave C real adapters. Real adapters (Bkash, Nagad, Stripe, etc.) are Wave C territory.
> **Read first (every sub-prompt):** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md) — cross-cutting decisions.

Sub-prompts:
1. [5a — MockAdapter + RedisTokenService](#prompt-5a--adapters-mockadapter--redistokenservice)
2. [5b — Resilience4j + WireMock harness](#prompt-5b--adapters-resilience4j--wiremock-harness)

---

## Prompt 5a — adapters MockAdapter + RedisTokenService

```
You are starting the `conflux-adapters` Wave A scope on branch `phase-1/adapters`. This is the FIRST of TWO sub-prompts (5a → 5b).

Phase 0 already shipped the locked port types (`PaymentProvider`, `Vendor`, `VendorPaymentRequest`, `VendorRefundRequest`, `VendorCredentials`, `VendorResponse`, `VendorStatus`), `PaymentProviderRegistry`, `MfsAdapterException`, `HttpClientFactory`, `NoopTokenService` (placeholder), and `TokenService` (interface). DO NOT modify the locked ones; you WILL delete `NoopTokenService` and its test.

Your sub-scope: `MockAdapter`, `RedisTokenService` (replacing `NoopTokenService`), `VendorAuthClient` interface + `MockVendorAuthClient`, `MockErrorMapper`, deletion of `NoopTokenService` + `NoopTokenServiceTest`.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md "Cross-cutting decisions" section
- conflux-adapters/CLAUDE.md
- DOCS/features/adapters/PRD.md (full)
- DOCS/features/adapters/TECH_SPEC.md (full)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/ (all locked types)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/support/ (HttpClientFactory, NoopTokenService, TokenService, PaymentProviderRegistry)
- conflux-adapters/src/test/.../NoopTokenServiceTest.java (you will delete this)

WORK ONLY IN
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/{mock,support}/...
- conflux-adapters/src/test/...
- conflux-application/src/test/java/pay/conflux/backend/adapters/...

DO NOT TOUCH
- The locked port types.
- conflux-common/, any other module.
- Root pom.xml.
- DOCS/contracts/openapi.json.

DELIVERABLES

1. `MockAdapter` (`pay.conflux.backend.adapters.mock.MockAdapter`):
   - `@Component`. `implements PaymentProvider`.
   - `supports(Vendor v)` → `v == Vendor.MOCK`.
   - `initiate(VendorPaymentRequest req, VendorCredentials creds)`:
     - Read `req.metadata().get("mock_outcome")`. Switch:
       - `"success"` → `VendorResponse(COMPLETED, "MOCK-" + req.transactionId(), null, "{\"ok\":true}", null)`.
       - `"fail"` → `VendorResponse(FAILED, "MOCK-" + req.transactionId(), null, "{\"err\":\"failed\"}", ErrorCode.MFS_ADAPTER_FAILURE)`.
       - `"cancel"` → `VendorResponse(CANCELLED, "MOCK-" + req.transactionId(), null, "{\"err\":\"cancelled\"}", ErrorCode.MFS_ADAPTER_FAILURE)`.
       - `"insufficient_funds"` → ErrorCode.INSUFFICIENT_FUNDS.
       - default (no key) → `VendorResponse(INITIATED, "MOCK-" + req.transactionId(), "https://mock.conflux.local/pay/MOCK-" + req.transactionId(), "{}", null)`.
     - Side-effect-free; no I/O; deterministic given inputs.
   - `queryStatus(String vendorTrxId, VendorCredentials creds)`:
     - Deterministic rule: if `vendorTrxId` ends with `-pending` → INITIATED; ends with `-completed` → COMPLETED; ends with `-failed` → FAILED; else COMPLETED. Document the convention.
   - `refund(VendorRefundRequest req, VendorCredentials creds)`:
     - Read `req.metadata?.get("mock_refund_outcome")` defaulting to success → `VendorResponse(COMPLETED, "REFUND-" + req.transactionId(), null, "{}", null)`.

2. `VendorAuthClient` interface + `MockVendorAuthClient` (`pay.conflux.backend.adapters.support`):
   - Interface: `record AuthToken(String token, Instant expiresAt) {}` + `AuthToken authenticate(Vendor v, VendorCredentials creds)`.
   - Mock impl: returns `AuthToken("mock-token", Instant.now().plusSeconds(3600))` for `MOCK`; throws `UnsupportedOperationException` for any other vendor (Wave C will provide real impls per vendor).

3. `RedisTokenService implements TokenService` (`pay.conflux.backend.adapters.support.RedisTokenService`):
   - `@Component @Primary` (so Spring picks it over any leftover NoopTokenService stub if it exists during the transition).
   - `String getToken(Vendor v, VendorCredentials creds)`:
     1. Hash the credentials: `String credsHash = sha256(canonicalize(creds.values()))`. Cache key `adapter:token:{v.name()}:{credsHash}`. Hashing prevents accidental logging of credentials.
     2. `GET` from Redis. If hit AND `expiresAt - now > 60s` → return.
     3. Else: acquire a per-key distributed lock via `SET adapter:token:lock:{v.name()}:{credsHash} 1 EX 5 NX`. If lock not acquired (someone else is fetching), wait + poll the cache with a small backoff (max 4s total wait), then return cached value or throw `MfsAdapterException(VENDOR_DOWN, "Token fetch contention")`.
     4. With lock held: re-check cache (double-check pattern). If still missing → call `vendorAuthClient.authenticate(v, creds)` → store with `SETEX = expiresAt - now - 60s`. Release lock.
     5. For `MOCK`: still go through the same code path (the mock client returns instantly). This validates the wiring even on the mock.
   - Never log the token, the credentials, or the credsHash. The structured log emitted on auth-call should include only the vendor name and a one-time correlation id.

4. `MockErrorMapper implements ErrorMapper` (`pay.conflux.backend.adapters.support.ErrorMapper` is a NEW interface you create):
   - Interface: `ErrorCode map(String vendorCode)`.
   - Mock impl handles a canonical set: `"INSUFFICIENT_FUNDS"` → `ErrorCode.INSUFFICIENT_FUNDS`; `"VENDOR_DOWN"` → `ErrorCode.VENDOR_DOWN`; default → `ErrorCode.MFS_ADAPTER_FAILURE`. NEVER returns null. NEVER returns `INTERNAL_ERROR`.

5. **Delete** `conflux-adapters/src/main/java/pay/conflux/backend/adapters/support/NoopTokenService.java` AND `conflux-adapters/src/test/.../NoopTokenServiceTest.java`. Reason: replaced by `RedisTokenService`. Document the deletion in the commit message.

TESTS (target: 70% module coverage on this sub-prompt)

Unit:
- `MockAdapter`: every documented `mock_outcome` produces the expected `VendorResponse`. Default-no-metadata returns INITIATED with the deterministic redirect URL.
- `MockAdapter.supports(MOCK) == true`; `supports(BKASH) == false`.
- `MockErrorMapper`: every documented vendor code; unknown codes map to `MFS_ADAPTER_FAILURE`. Property test (jqwik): never null, never `INTERNAL_ERROR`.

Integration (Testcontainers Redis):
- `RedisTokenService.getToken(MOCK, creds)`: first call invokes `MockVendorAuthClient.authenticate` exactly once; second call hits cache, no auth-client invocation.
- **Distributed lock test**: 10 parallel threads call `getToken(MOCK, creds)` simultaneously with cold cache. Spy on `MockVendorAuthClient` — exactly one invocation across all 10 threads.
- Credential change: calling with different `VendorCredentials.values` produces different cache keys (different invocation count).
- `PaymentProviderRegistry.lookup(MOCK)` returns the `MockAdapter` (autowiring works); `lookup(BKASH)` throws `MfsAdapterException`.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-adapters -am verify` BUILD SUCCESS.
- JaCoCo ≥ 70%.
- `NoopTokenService` and `NoopTokenServiceTest` no longer exist (`git ls-files | grep -i noop` returns nothing).
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(adapters): MockAdapter + RedisTokenService (5a) — replaces NoopTokenService`.

FORBIDDEN
- Implementing `BkashAdapter`, `NagadAdapter`, `StripeAdapter` (Wave C).
- Caching `VendorCredentials` instances anywhere — only the *derived token* is cached.
- Logging credentials, tokens, or anything that could re-derive them.
- Modifying any locked port/record type, the Vendor enum, the registry, or any other module.
- Adding a root-pom dep.
- Implementing Resilience4j wiring (5b) or the WireMock harness (5b).

Output: file tree, sample Redis key trace from a token-fetch, distributed-lock test log (showing exactly 1 invocation for 10 threads), JaCoCo tail.
```

---

## Prompt 5b — adapters Resilience4j + WireMock harness

```
You are completing the `conflux-adapters` Wave A scope on branch `phase-1/adapters`. Prompt 5a is committed. Your sub-scope: Resilience4j circuit-breaker wiring per vendor, the `AdapterResilience` helper, the `VendorWireMockExtension` test harness Wave C agents will reuse, and the final coverage push.

`resilience4j-spring-boot3` is already in root pom from Phase 0. You don't need to add it.

READ FIRST
- DOCS/features/adapters/TECH_SPEC.md §3 + §6 (resiliency requirements)
- conflux-adapters/CLAUDE.md "isolated OkHttpClient" gotcha
- Resilience4j docs (Context7 MCP if available): focus on `CircuitBreakerRegistry`, `@CircuitBreaker`, `Decorators.ofCallable(...)`.
- The 5a commits

DELIVERABLES

1. `ResilienceConfig` (`pay.conflux.backend.adapters.config.ResilienceConfig`):
   - One `CircuitBreaker` per `Vendor` enum value: bkash, nagad, rocket, upay, pathao, mcash, stripe, mock.
   - Config per breaker:
     - `slidingWindowType=COUNT_BASED`, `slidingWindowSize=20`
     - `failureRateThreshold=50` (%)
     - `waitDurationInOpenState=Duration.ofSeconds(30)`
     - `slowCallDurationThreshold=Duration.ofSeconds(10)`
     - `slowCallRateThreshold=50`
     - `permittedNumberOfCallsInHalfOpenState=3`
   - Register each in a single `CircuitBreakerRegistry` bean for inspection in tests.

2. `AdapterResilience` (`pay.conflux.backend.adapters.support.AdapterResilience`):
   - `@Component`. Helper method:
     - `<T> T executeWithCircuitBreaker(Vendor v, java.util.concurrent.Callable<T> call) throws MfsAdapterException`.
   - Resolves the per-vendor `CircuitBreaker` from the registry; decorates the callable; on `CallNotPermittedException` (circuit open) → throw `MfsAdapterException(VENDOR_DOWN, "Circuit open for " + v)`.
   - On the underlying call's checked exceptions → wrap in `MfsAdapterException(MFS_ADAPTER_FAILURE, ...)`.
   - The mock adapter does NOT need to use this helper (no real I/O), but real adapters in Wave C will. Provide it now.

3. WireMock test harness (`conflux-application/src/test/java/pay/conflux/backend/adapters/wiremock/`):
   - `VendorWireMockExtension implements BeforeEachCallback, AfterEachCallback`:
     - On `beforeEach`: start `WireMockServer` on a random port, expose `String getBaseUrl()`.
     - On `afterEach`: stop + verify no unmatched requests (assert `WireMock.findUnmatchedRequests().getRequests().isEmpty()`).
   - `VendorScenarios` — static factories that register stubs:
     - `success(Vendor v)` — registers a vendor-specific success response shape.
     - `vendorError(Vendor v, String code)` — error response with the vendor's native code.
     - `timeout(Vendor v, long ms)` — fixed-delay response.
     - `unauthorizedThenSuccess(Vendor v)` — first call returns 401, second returns 200 (exercises the token-refresh retry).
   - Self-test: a single integration test in `conflux-application/src/test/.../adapters/MockTokenRefreshIT.java` that:
     - Spins up the harness with `VendorScenarios.unauthorizedThenSuccess(MOCK)`.
     - Wires a `WireMockMockVendorAuthClient` (test-only override of `MockVendorAuthClient`) that hits the WireMock URL.
     - Invokes `RedisTokenService.getToken(MOCK, ...)`.
     - Asserts: exactly TWO HTTP calls to WireMock, second one returns the token.

4. **Isolation test** (`conflux-application/src/test/.../adapters/HttpClientIsolationIT.java`):
   - Two `WireMockServer` instances simulating slow `bkash` (200ms response) and fast `nagad` (immediate). Use the existing `HttpClientFactory` from Phase 0 to ensure separate `OkHttpClient` instances.
   - 50 concurrent calls split across both vendors. Assert nagad p95 latency < 50ms (unaffected by bkash slowdown).
   - This is the test Wave C real adapters will rely on as proof that connection-pool isolation holds.

5. **Circuit-breaker test** (`conflux-application/src/test/.../adapters/CircuitBreakerIT.java`):
   - Force 11 consecutive failures via `AdapterResilience.executeWithCircuitBreaker(MOCK, () -> { throw new RuntimeException("boom"); })`. The 12th call short-circuits with `MfsAdapterException(VENDOR_DOWN)` without invoking the callable.
   - After waiting the configured `waitDurationInOpenState` (in tests, override to 1 second via `@DynamicPropertySource`), the breaker transitions to half-open and accepts a probe.

6. Coverage push to ≥ 80% on `conflux-adapters`.

TESTS — see deliverables 3, 4, 5 above. Plus:

Unit:
- `AdapterResilience.executeWithCircuitBreaker` with a working callable returns the value; with a failing callable propagates `MfsAdapterException`; with circuit open throws `VENDOR_DOWN`.
- `VendorWireMockExtension` afterEach correctly fails the test if there were unmatched requests (verify via a deliberate test that calls an un-stubbed endpoint).

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All from 5a still hold.
- JaCoCo ≥ 80% on `conflux-adapters`.
- All four ITs (MockTokenRefresh, HttpClientIsolation, CircuitBreaker, plus 5a's existing token-cache test) green.
- WireMock harness usable from a future Wave C agent's test (the self-test in deliverable 3 IS the proof).
- gitleaks, Spotless, PMD clean.
- Commit: `feat(adapters): Resilience4j + WireMock harness (5b) — closes Wave A adapters`.

FORBIDDEN
- Implementing `BkashAdapter`/`NagadAdapter`/`StripeAdapter` (Wave C).
- Sharing one global `OkHttpClient` across vendors.
- Caching `VendorCredentials` (still — same as 5a).
- Modifying any locked type.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample circuit-breaker state-transition trace, sample isolation-test latency histogram (bkash vs nagad p95), JaCoCo final tail.
```
