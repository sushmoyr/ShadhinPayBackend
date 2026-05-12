# Phase 1 Wave C — `BkashAdapter` prompts

> **Branch:** `phase-1/adapter-bkash` — single sub-prompt (10), parallel with sub-prompt 11 (SSLCommerz). **Sub-prompt 0 must already be merged on `main`.**
> **Scope:** ship `BkashAdapter` (`PaymentProvider`), `BkashAuthClient` (`VendorAuthClient`), `BkashErrorMapper`, and full WireMock contract coverage. **No new schema, no new ErrorCode, no controller. No edits to the locked `PaymentProvider` interface.**
> **Read first:** [Wave C index](../PHASE_1_WAVE_C_PROMPTS.md); `conflux-adapters/CLAUDE.md`; `DOCS/features/adapters/PRD.md`; `DOCS/features/adapters/TECH_SPEC.md` §3, §4.1, §6; the Wave A WireMock harness under `conflux-application/src/test/.../adapters/wiremock/VendorWireMockExtension.java`; vendor docs at https://developer.bka.sh/docs (Bkash **Tokenized** Checkout v1.2 — Auth, Create, **Execute**, Query, Refund — note that all Tokenized paths are prefixed with `/tokenized`).

---

## Prompt 10 — BkashAdapter (full module, single sub-prompt)

```
You are running Wave C sub-prompt 10 on branch `phase-1/adapter-bkash`. Sub-prompt 0 is on `main` (`Vendor.SSLCOMMERZ` added, V1016 applied, `PartnerCredentialsConfig.sslcommerz` field present). Wave B is on `main`. Your sub-scope: a complete `BkashAdapter` for Bkash Tokenized Checkout v1.2, plus the `VendorAuthClient` implementation for Bkash's `grant_token` flow, plus the `ErrorMapper` for Bkash's documented error codes, plus the WireMock contract suite.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_C_PROMPTS.md (full, especially Cross-cutting decisions 1, 3, 6, 7)
- conflux-adapters/CLAUDE.md
- DOCS/features/adapters/PRD.md (full)
- DOCS/features/adapters/TECH_SPEC.md §3, §4.1, §6
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/*.java (all six locked port files)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/support/{TokenService,VendorAuthClient,HttpClientFactory,AdapterResilience,ErrorMapper,PaymentProviderRegistry}.java
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/mock/MockAdapter.java (canonical adapter shape)
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/support/*Test.java (the harness patterns)
- conflux-common/src/main/java/pay/conflux/backend/common/error/ErrorCode.java (the closed enum — your mapper picks from these, never adds)

WORK ONLY IN
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/bkash/...
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/bkash/...

(No `pom.xml` edits. WireMock `org.wiremock:wiremock-jetty12` 3.10.0 is already on the `conflux-adapters` test classpath; OkHttp is on the main classpath. If you find anything missing, STOP and escalate — do not add dependencies.)

DO NOT TOUCH
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/{port,support,config,mock,sslcommerz,events}/.
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/error/ — you MAY throw the existing `MfsAdapterException` and any `ApiOperationException` subclass from `conflux-common/error/`; you may NOT add new exception subclasses in this package.
- conflux-payment-core, conflux-provisioning, conflux-common (all read-only).
- application.yml — the `conflux.adapters.partner-credentials.bkash.*` keys were decided in Wave B sub-prompt 7b; consume them via `PartnerCredentialsConfig`, do not edit.
- Vendor.java (locked; SSLCOMMERZ was added in sub-prompt 0).
- ResilienceConfig (registry self-seeds for BKASH).
- `PaymentProvider` interface — adding `confirm(...)` to the port is forbidden (it's a Wave A locked contract). The Execute step lives as a public method on `BkashAdapter` only; see deliverable 1b.
- Any Flyway migration.

DELIVERABLES

1. **Package** `pay.conflux.backend.adapters.bkash` with three files:

   a. `BkashAuthClient` (`@Component`) — implements `VendorAuthClient`. POST to `{baseUrl}/tokenized/checkout/token/grant` with JSON body
      `{"app_key":"<appKey>","app_secret":"<appSecret>"}`
      and headers
      `username: <username>`, `password: <password>`, `Content-Type: application/json`, `Accept: application/json`.
      (Credentials are pulled from the `VendorCredentials` map keys `appKey`, `appSecret`, `username`, `password`, `baseUrl` — camelCase per Spring relaxed binding from `application.yml`.)
      Parses the response `{ "id_token": "...", "expires_in": 3600, "refresh_token": "...", "token_type": "Bearer" }` into `AuthToken(token = id_token, expiresAt = now + expires_in - 60s)` (60s safety margin so a cached token never expires mid-call). On HTTP 4xx/5xx or malformed body, throws `MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, ...)` — DO NOT swallow.
      **`supports(Vendor.BKASH)` is implicit:** `authenticate(...)` must verify `v == Vendor.BKASH`; throws `IllegalArgumentException` otherwise. (Defense-in-depth — the `TokenService` is shared across vendors.)

   b. `BkashAdapter` (`@Component`) — implements `PaymentProvider`. Owns an isolated `OkHttpClient` obtained via `httpClientFactory.clientFor(Vendor.BKASH)` (per-vendor 5s connect, 10s read/write — provided by the factory). Injects `HttpClientFactory`, `TokenService`, `AdapterResilience`, `BkashErrorMapper`, and `ObjectMapper`. **Does NOT inject `PartnerCredentialsConfig`** — credentials always flow in via the `VendorCredentials` argument, which `payment-core`'s dispatch has already resolved (PARTNER vs CUSTOM). `supports(v) → v == Vendor.BKASH`.

      Methods (all four outbound HTTP calls are wrapped in `adapterResilience.executeWithCircuitBreaker(Vendor.BKASH, () -> ...)`; a `MfsAdapterException(VENDOR_DOWN, ...)` from the breaker is caught at the adapter boundary and returned as `VendorResponse(FAILED, null, null, "circuit_open", VENDOR_DOWN)` — never thrown past the adapter):

      - `initiate(VendorPaymentRequest, VendorCredentials)`:
        1. Acquire token: `tokenService.getToken(Vendor.BKASH, creds)` (Redis-cached; falls through to `BkashAuthClient.authenticate` on miss).
        2. POST `{baseUrl}/tokenized/checkout/create` with JSON body
           `{"mode":"0011","payerReference":" ","callbackURL":"<callback>","amount":"<amount>","currency":"BDT","intent":"sale","merchantInvoiceNumber":"<transactionId>"}`
           and headers `Authorization: <id_token>`, `X-APP-Key: <appKey>`, `Content-Type: application/json`, `Accept: application/json`.
        3. Map response: `statusCode == "0000"` → `VendorResponse(INITIATED, paymentID, bkashURL, rawBody, null)`. Anything else → mapper-routed `ErrorCode` + `VendorStatus.FAILED`.
        4. On HTTP 401: refresh token EXACTLY ONCE (`tokenService.invalidate(Vendor.BKASH, creds)` + retry), then re-attempt. Second 401 → `VendorStatus.FAILED` + `MFS_ADAPTER_FAILURE`. Never loop.

      - `confirm(String paymentID, VendorCredentials)` — **NEW public method on `BkashAdapter` ONLY (not on the `PaymentProvider` port).** This is the Tokenized Checkout v1.2 Execute step that captures the payment after the user authorizes at the `bkashURL`. The Bkash payment lifecycle is: `initiate → create → user redirects to bkashURL → user authorizes → Bkash redirects back to callbackURL with paymentID → server calls Execute → payment is captured`.
        1. Acquire token.
        2. POST `{baseUrl}/tokenized/checkout/execute` with body `{"paymentID":"<paymentID>"}` and the same auth headers as initiate.
        3. Map response: `statusCode == "0000"` AND `transactionStatus == "Completed"` → `VendorResponse(COMPLETED, paymentID, null, raw, null)`. Else mapper-routed failure.
        4. **CROSS-CUTTING NOTE (out of Wave C scope):** `payment-core`'s redirect-callback path (shipped in Wave B sub-prompt 8b) currently assumes the vendor captures during `initiate`. For Bkash, the callback handler must call `BkashAdapter.confirm(paymentID, creds)` before marking the transaction COMPLETED. This wiring is **NOT** part of sub-prompt 10 — `BkashAdapter` exposes the method as a public adapter-specific surface, and a follow-up cross-cutting prompt will adjust `payment-core`'s callback dispatcher. Document this gap in the `BkashAdapter` class-level Javadoc with a `// FOLLOW-UP: payment-core callback must invoke BkashAdapter.confirm(...) on Bkash redirect; see Wave C report for tracker.` comment.

      - `queryStatus(String paymentID, VendorCredentials)`:
        1. Acquire token.
        2. POST `{baseUrl}/tokenized/checkout/payment/status` with `{"paymentID":"<paymentID>"}` and the same auth headers.
        3. Map `transactionStatus`: `"Completed"` → `COMPLETED`; `"Initiated"` / `"Authorized"` → `INITIATED`; `"Cancelled"` → `CANCELLED`; `"Failed"` / anything else → `FAILED` + mapped `ErrorCode`.

      - `refund(VendorRefundRequest, VendorCredentials)`:
        1. Acquire token.
        2. POST `{baseUrl}/tokenized/checkout/payment/refund` with `{"paymentID":"<vendorTrxId>","amount":"<amount>","trxID":"<originalTrxId>","sku":"<reasonOrEmpty>","reason":"<reasonOrEmpty>"}`. Bkash requires BOTH `sku` and `reason`, each ≤ 255 chars; if `request.reason()` is null, supply `""` for both.
        3. Map `statusCode == "0000"` + `transactionStatus == "Completed"` → `VendorResponse(COMPLETED, refundTrxID, null, raw, null)`; else mapped failure.

      - `supports(Vendor)` as above.

      **Logging discipline:** Adapter MUST NOT log `creds`, `id_token`, full request bodies, or full response bodies. Use `info`-level structured logs with the masked correlation only:
      `log.info("BKASH initiate businessTrx={} vendorPaymentID={} status={}", transactionId, paymentID, status)`. Error logs include only the exception class + HTTP status code, never the response body or stack trace from the vendor. **These secret-redaction rules apply equally to all artifacts you produce — code, log lines, JavaDocs, commit messages, and any trace samples in your output report.** Mask `app_secret`, `id_token`, full session/payment IDs (keep first 4 chars + `***` + last 4), and `bank_tran_id` if encountered.

   c. `BkashErrorMapper` — pure static class. Method: `ErrorCode map(String bkashStatusCode)`. Maps the documented Bkash codes:
      | bKash code | Meaning                          | → ErrorCode                                          |
      |------------|----------------------------------|------------------------------------------------------|
      | `"0000"`   | Success                          | (no error — adapter handles this branch separately)  |
      | `"2001"`   | Validation error                 | `VALIDATION_ERROR`                                   |
      | `"2002"`   | Token expired / invalid          | `MFS_ADAPTER_FAILURE` (adapter retries once)         |
      | `"2003"`   | Auth failed                      | `UNAUTHORIZED`                                       |
      | `"2023"`   | Insufficient funds               | `INSUFFICIENT_FUNDS`                                 |
      | `"2024"`   | Daily limit exceeded             | `INSUFFICIENT_FUNDS`                                 |
      | `"2056"`   | Duplicate transaction            | `RESOURCE_ALREADY_EXISTS`                            |
      | `"503"`    | Service unavailable              | `VENDOR_DOWN`                                        |
      | default    | anything else, incl. unknown     | `MFS_ADAPTER_FAILURE`                                |

      Add a class-level Javadoc note: "Status codes are drawn from the bKash merchant onboarding PDF (`bkash.devarif.me/doc.pdf` and community SDKs); the public developer portal does NOT publish an exhaustive enumeration. The default → `MFS_ADAPTER_FAILURE` fallthrough is the durable hook for any code not in this table." All `ErrorCode` values referenced are present in `conflux-common/src/main/java/pay/conflux/backend/common/error/ErrorCode.java` — verified at prompt authoring time.

2. **Resilience wiring already covered above.** All four outbound calls (auth, create, execute, status, refund) go through `adapterResilience.executeWithCircuitBreaker(Vendor.BKASH, callable)`. Do NOT call `circuitBreakerRegistry.circuitBreaker(...)` directly — the helper does that internally. A circuit-open state surfaces as `MfsAdapterException(VENDOR_DOWN, "Circuit open for BKASH")`, which `BkashAdapter` catches at the method boundary and converts to `VendorResponse(FAILED, null, null, "circuit_open", VENDOR_DOWN)`.

3. **Catch-clause guidance.** Catching `RuntimeException` or `Exception` broadly is forbidden. Catch specific types:
   - `IOException` from `OkHttpClient.newCall().execute()` — map socket failures to the mapper, route timeouts (`SocketTimeoutException`) specifically to `VENDOR_DOWN`.
   - `JsonProcessingException` (Jackson) — vendor returned malformed JSON; map to `MFS_ADAPTER_FAILURE`.
   - `MfsAdapterException` from `AdapterResilience` — re-throw or convert to `VendorResponse(FAILED, ..., VENDOR_DOWN)` at the method boundary; do not wrap further.
   - All other exception types are programmer errors — let them propagate.

TESTS (target: 80% line, 70% branch on the `bkash` package)

WireMock contract suite (`BkashAdapterContractTest`) — extends Wave A's `VendorWireMockExtension`:
- `initiate_success_returnsRedirectUrl` — WireMock stubs `/tokenized/checkout/token/grant`, `/tokenized/checkout/create` → 200 + `statusCode: "0000"` + valid `bkashURL`. Assert `VendorStatus.INITIATED`, `redirectUrl != null`, `vendorTrxId == paymentID`.
- `initiate_insufficientFunds_mapsToInsufficientFunds` — `/tokenized/checkout/create` → 200 + `statusCode: "2023"`. Assert `FAILED` + `INSUFFICIENT_FUNDS`.
- `initiate_duplicateTransaction_mapsToResourceAlreadyExists` — `statusCode: "2056"`. Assert `FAILED` + `RESOURCE_ALREADY_EXISTS`.
- `initiate_401ThenSuccess_refreshesTokenExactlyOnce` — `/tokenized/checkout/create` → 401 then 200. Assert success AND `verify(2, postRequestedFor(urlEqualTo("/tokenized/checkout/create")))` AND `verify(2, postRequestedFor(urlEqualTo("/tokenized/checkout/token/grant")))` (one initial + one refresh).
- `initiate_doubleAuthFailure_doesNotLoop` — `/tokenized/checkout/create` → 401 twice. Assert `FAILED` + `MFS_ADAPTER_FAILURE`; `verify(2, postRequestedFor(... create ...))` (no third attempt).
- `initiate_timeout_mapsToVendorDown` — `/tokenized/checkout/create` → WireMock fixed-delay > 10s. Assert `FAILED` + `VENDOR_DOWN`. (Socket timeouts always map to `VENDOR_DOWN`, matching the SSLCommerz adapter convention — no per-adapter discretion.) Test must complete in < 12s wall-clock; do not exceed the call-timeout budget.
- `confirm_success_capturesPayment` — stub `/tokenized/checkout/execute` → 200 + `statusCode: "0000"` + `transactionStatus: "Completed"`. Assert `VendorStatus.COMPLETED`, `vendorTrxId == paymentID`.
- `confirm_failure_mapsViaErrorMapper` — `/tokenized/checkout/execute` → 200 + `statusCode: "2003"` + `transactionStatus: "Failed"`. Assert `FAILED` + `UNAUTHORIZED`.
- `queryStatus_completed_returnsCompleted` / `queryStatus_pending_returnsInitiated` / `queryStatus_cancelled_returnsCancelled` — three obvious cases using `/tokenized/checkout/payment/status`.
- `refund_success` / `refund_insufficient_refund_funds` — `/tokenized/checkout/payment/refund`. Bkash returns a different code for refund-side insufficient funds; route via mapper.
- `circuitBreakerOpen_failsClosedWithoutCallingVendor` — pre-trip the breaker by recording N synthetic failures into the registry, then assert the adapter returns `FAILED` + `VENDOR_DOWN` without WireMock receiving any request (`verify(0, ...)`).

Unit tests:
- `BkashErrorMapperTest` — every documented code maps to the expected `ErrorCode`; unknown code falls through to `MFS_ADAPTER_FAILURE`.
- `BkashErrorMapperJqwikTest` — jqwik property: `forAll String code` (alphanumeric, length ≤ 8) → `mapper.map(code) != null` AND `mapper.map(code) != ErrorCode.INTERNAL_ERROR`. (Exhaustiveness invariant — adapter problems never bleed into `INTERNAL_ERROR`.)
- `BkashAuthClientTest` (WireMock-based, NOT MockWebServer — WireMock is already on the test classpath) — happy path returns `AuthToken` with `expiresAt = now + 3540s` (1 hour − 60s safety margin); 401 throws `MfsAdapterException(MFS_ADAPTER_FAILURE, ...)`; malformed JSON throws same.

Integration test (uses `RedisTokenServiceIntegrationTest` pattern):
- `BkashAdapter_cachesTokenAcrossCalls` — two consecutive `initiate` calls hit `/grant` ONCE then `/create` twice. Verifies Redis-backed `TokenService` is being used.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-adapters -am verify` BUILD SUCCESS.
- JaCoCo: bkash package ≥ 80% line, ≥ 70% branch. Adapters module aggregate stays ≥ 80%.
- `mvn -pl conflux-application -am verify` BUILD SUCCESS — no Modulith / ArchUnit regressions.
- gitleaks, Spotless, PMD, SpotBugs clean.
- Single commit (or up to two): `feat(adapters): BkashAdapter + VendorAuthClient + ErrorMapper (wave-c sub-prompt 10)`.

FORBIDDEN
- Implementing any other vendor (SSLCommerz lives in sub-prompt 11; NAGAD / ROCKET / UPAY / PATHAO / MCASH / STRIPE are Wave D).
- Editing `Vendor.java`, `ResilienceConfig`, `TokenService`, `VendorAuthClient` (interface), `PaymentProvider` (interface — do NOT add `confirm` to the port), or any locked port.
- Adding a new `ErrorCode`.
- Persisting, logging, or caching `VendorCredentials` plaintext.
- Caching tokens anywhere except via `TokenService` (Redis-backed; the shared interface enforces TTL).
- Sharing the OkHttpClient with any other adapter (use `httpClientFactory.clientFor(Vendor.BKASH)` exclusively).
- Catching `RuntimeException` or `Exception` broadly — catch the specific types enumerated in deliverable 3.
- Editing `payment-core`'s callback path — the Bkash Execute integration is flagged as a follow-up cross-cutting concern; this sub-prompt only EXPOSES `BkashAdapter.confirm(...)`.

Output: file tree, the WireMock contract test names, JaCoCo tail for the bkash package, one curl-style trace of the `initiate` happy path (request + response bodies with secrets MASKED per the redaction rules above — `app_secret` and `id_token` masked, payment IDs masked as `<first4>***<last4>`).
```
