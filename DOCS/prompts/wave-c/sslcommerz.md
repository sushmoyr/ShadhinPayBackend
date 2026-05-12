# Phase 1 Wave C — `SslcommerzAdapter` prompts

> **Branch:** `phase-1/adapter-sslcommerz` — single sub-prompt (11), parallel with sub-prompt 10 (Bkash). **Sub-prompt 0 must already be merged on `main`.**
> **Scope:** ship `SslcommerzAdapter` (`PaymentProvider`) and `SslcommerzErrorMapper`, plus WireMock contract coverage. **No `VendorAuthClient` impl** — SSLCommerz authenticates per-request with `store_id` + `store_passwd`, no token endpoint. **No new schema, no new ErrorCode, no controller.**
> **Read first:** [Wave C index](../PHASE_1_WAVE_C_PROMPTS.md); `conflux-adapters/CLAUDE.md`; `DOCS/features/adapters/PRD.md`; `DOCS/features/adapters/TECH_SPEC.md` §3, §6; the Wave A WireMock harness; vendor docs at https://developer.sslcommerz.com (Hosted Checkout v4 — `/gwprocess/v4/api.php` for initiation, `/validator/api/validationserverAPI.php` for status, `/validator/api/merchantTransIDvalidationAPI.php` for the merchant-tx lookup, refund via the same validator with `bank_tran_id`).

---

## Prompt 11 — SslcommerzAdapter (full module, single sub-prompt)

```
You are running Wave C sub-prompt 11 on branch `phase-1/adapter-sslcommerz`. Sub-prompt 0 is on `main` (`Vendor.SSLCOMMERZ` added, V1016 applied, `PartnerCredentialsConfig.sslcommerz` field present). Wave B is on `main`. Sub-prompt 10 (Bkash) is running in parallel on its own branch and will not conflict with yours — you both only consume locked interfaces. Your sub-scope: a complete `SslcommerzAdapter` for SSLCommerz Hosted Checkout v4, plus the `ErrorMapper`, plus the WireMock contract suite. **No `VendorAuthClient` impl** — see step (1) below.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_C_PROMPTS.md (full, especially Cross-cutting decisions 1, 3, 6, 7)
- conflux-adapters/CLAUDE.md
- DOCS/features/adapters/PRD.md (full)
- DOCS/features/adapters/TECH_SPEC.md §3, §6
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/*.java (all six locked port files)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/support/{HttpClientFactory,AdapterResilience,ErrorMapper,PaymentProviderRegistry}.java (you will use HttpClientFactory + AdapterResilience; you will NOT use TokenService or VendorAuthClient)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/mock/MockAdapter.java
- conflux-common/src/main/java/pay/conflux/backend/common/error/ErrorCode.java

WORK ONLY IN
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/sslcommerz/...
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/sslcommerz/...

(No `pom.xml` edits. WireMock `org.wiremock:wiremock-jetty12` 3.10.0 is already on the `conflux-adapters` test classpath; OkHttp is on the main classpath. If you find anything missing, STOP and escalate — do not add dependencies.)

DO NOT TOUCH
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/{port,support,config,mock,bkash,events}/.
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/error/ — you MAY throw the existing `MfsAdapterException` and any `ApiOperationException` subclass from `conflux-common/error/`; you may NOT add new exception subclasses in this package.
- conflux-payment-core, conflux-provisioning, conflux-common (all read-only).
- application.yml — `conflux.adapters.partner-credentials.sslcommerz.*` was added in sub-prompt 0; consume via `PartnerCredentialsConfig`, do not edit.
- Vendor.java, ResilienceConfig (registry self-seeds for SSLCOMMERZ from sub-prompt 0).
- Any Flyway migration.

DELIVERABLES

1. **No `VendorAuthClient` impl.** SSLCommerz Hosted Checkout v4 does not issue a separate session token — every request includes `store_id` and `store_passwd` directly in the form-encoded body. Your adapter MUST NOT call `tokenService.getToken(...)`. Document this in the class-level Javadoc of `SslcommerzAdapter`:
   > "SSLCommerz authenticates per-request with `store_id` + `store_passwd` in the form body — no `grant_token` flow, so this adapter intentionally does NOT inject `TokenService` or implement `VendorAuthClient`."

2. **Package** `pay.conflux.backend.adapters.sslcommerz` with two files:

   a. `SslcommerzAdapter` (`@Component`) — implements `PaymentProvider`. Owns an isolated `OkHttpClient` obtained via `httpClientFactory.clientFor(Vendor.SSLCOMMERZ)` (per-vendor 5s connect, 10s read/write — provided by the factory). Injects `HttpClientFactory`, `AdapterResilience`, `SslcommerzErrorMapper`, and `ObjectMapper`. **Does NOT inject `PartnerCredentialsConfig`** — credentials always flow in via the `VendorCredentials` argument, which `payment-core`'s dispatch has already resolved. `supports(v) → v == Vendor.SSLCOMMERZ`.

      `VendorCredentials` map keys (camelCase, per Spring relaxed binding): `storeId`, `storePasswd`, `baseUrl`. If a required key is missing from `creds`, throw `IllegalArgumentException("sslcommerz credentials missing key: " + key)`.

      Methods (all outbound calls wrapped in `adapterResilience.executeWithCircuitBreaker(Vendor.SSLCOMMERZ, ...)`; open-circuit surfaces as `VendorResponse(FAILED, null, null, "circuit_open", VENDOR_DOWN)`):

      - `initiate(VendorPaymentRequest, VendorCredentials)`:
        1. **Validate `transactionId` ≤ 30 chars** (SSLCommerz `tran_id` max length is 30); if longer, throw `IllegalArgumentException("sslcommerz tran_id exceeds 30-char limit")` — the caller should never produce one this large but the adapter is the last enforcement point.
        2. POST `{baseUrl}/gwprocess/v4/api.php` with form-encoded body (`Content-Type: application/x-www-form-urlencoded`):
           ```
           store_id=<storeId>
           store_passwd=<storePasswd>
           total_amount=<amount>
           currency=BDT
           tran_id=<transactionId>
           success_url=<callback>?status=success
           fail_url=<callback>?status=fail
           cancel_url=<callback>?status=cancel
           ipn_url=<callback>/ipn        # optional — set but harmless if no IPN handler exists; SSLCommerz will simply retry until it gives up
           product_name=<from metadata or "Payment">
           product_category=<from metadata or "Service">
           product_profile=general
           cus_name=<from metadata>
           cus_email=<from metadata>
           cus_phone=<from metadata>
           cus_add1=<from metadata or "N/A">
           cus_city=<from metadata or "Dhaka">
           cus_country=<from metadata or "Bangladesh">
           shipping_method=NO
           ```
           Map `request.metadata()` keys defensively — missing customer fields default to "N/A" as shown; SSLCommerz rejects empty strings on these. Document this defaulting in a class-level Javadoc table. **Class-level Javadoc must also note: `ipn_url` is set but unhandled — IPN delivery is best-effort and not required for status retrieval (the validator API is sufficient). This is intentional, not a bug.**
        3. Response is JSON: `{ "status": "SUCCESS", "sessionkey": "...", "GatewayPageURL": "...", ... }` or `{ "status": "FAILED", "failedreason": "...", ... }`.
        4. `status == "SUCCESS"` AND `GatewayPageURL != null` → `VendorResponse(INITIATED, sessionkey, GatewayPageURL, raw, null)`. Else mapper-routed `FAILED`.

      - `queryStatus(String tranId, VendorCredentials)`:
        1. **GET** `{baseUrl}/validator/api/merchantTransIDvalidationAPI.php?tran_id=<tranId>&store_id=<storeId>&store_passwd=<storePasswd>&format=json&v=1`. (SSLCommerz documents query-param auth on GET endpoints; this is the published mechanism, not a workaround.)
        2. Response: `{ "APIConnect": "DONE", "no_of_trans_found": "1", "element": [ { "status": "VALID"|"VALIDATED"|"PENDING"|"FAILED"|"CANCELLED", "tran_id": "...", "val_id": "...", "bank_tran_id": "...", ... } ] }`.
        3. Map `element[0].status`: `"VALID"` / `"VALIDATED"` → `COMPLETED`; `"PENDING"` → `INITIATED`; `"CANCELLED"` → `CANCELLED`; `"FAILED"` / `"INVALID_TRANSACTION"` / `"EXPIRED"` → `FAILED` + mapper code.
        4. `no_of_trans_found == "0"` → `VendorResponse(FAILED, null, null, raw, ErrorCode.RESOURCE_NOT_FOUND)`.

      - `refund(VendorRefundRequest, VendorCredentials)`:
        1. Refund requires the `bank_tran_id` (vendor's internal tx id). The request's `vendorTrxId` MUST carry this; if it instead carries the merchant `tran_id` (or session key), do a one-hop `queryStatus` first to fetch `bank_tran_id` from `element[0].bank_tran_id`. Document this hop in the Javadoc as a real-world SSLCommerz wart, not a vendor requirement (the validator response on the original transaction already includes `bank_tran_id`; the hop is a defensive fallback).
        2. **GET** `{baseUrl}/validator/api/merchantTransIDvalidationAPI.php` with query parameters (NOT a POST form body — SSLCommerz refund is GET with params):
           ```
           bank_tran_id=<...>
           refund_amount=<amount>
           refund_remarks=<reason or "merchant_initiated">
           refund_trans_id=<uniqueRefundId>        # REQUIRED since 2025-02-24, ≤ 30 chars, unique per refund
           store_id=<storeId>
           store_passwd=<storePasswd>
           v=1
           format=json
           ```
           Generate `refund_trans_id` as `"R-" + request.transactionId() + "-" + System.currentTimeMillis() % 1_000_000` (or any deterministic-yet-unique scheme ≤ 30 chars). Document the format in the Javadoc.
        3. Response: `{ "APIConnect": "DONE", "trans_id": "...", "ref_id": "...", "status": "success"|"failed"|"processing", "errorReason": "..." }`.
        4. Map `status`: `"success"` → `COMPLETED`; `"processing"` → `INITIATED`; `"failed"` → `FAILED` + mapper(errorReason).

      - `supports(Vendor)` as above.

      **Logging discipline:** Adapter MUST NOT log `creds`, `storePasswd`, or full request/response bodies. Use `info`-level structured logs with the masked correlation only: `log.info("SSLCOMMERZ initiate businessTrx={} sessionKey={} status={}", transactionId, maskedSessionKey, status)`. `maskedSessionKey` = first 4 chars + `"***"`. Error logs include only the exception class + HTTP status code, never the response body. **These secret-redaction rules apply equally to all artifacts you produce — code, log lines, JavaDocs, commit messages, and any trace samples in your output report.** Mask `store_passwd`, `bank_tran_id`, and session/`val_id` values (first 4 chars + `"***"`).

   b. `SslcommerzErrorMapper` — pure static class. Method: `ErrorCode map(String sslcommerzFailedReasonOrStatus)`. Maps SSLCommerz's free-form reason strings via substring match (case-insensitive):
      | substring                       | → ErrorCode               |
      |---------------------------------|---------------------------|
      | `"insufficient"`                | `INSUFFICIENT_FUNDS`      |
      | `"invalid card"` / `"invalid"`  | `VALIDATION_ERROR`        |
      | `"expired"` / `"expire"`        | `VALIDATION_ERROR`        |
      | `"declined"`                    | `MFS_ADAPTER_FAILURE`     |
      | `"timeout"` / `"timed out"`     | `VENDOR_DOWN`             |
      | `"duplicate"`                   | `DUPLICATE_RESOURCE`      |
      | `"unauthor"`                    | `UNAUTHORIZED`            |
      | `"cancel"`                      | `MFS_ADAPTER_FAILURE`     |
      | `null` or empty                 | `MFS_ADAPTER_FAILURE`     |
      | (anything else)                 | `MFS_ADAPTER_FAILURE`     |
      Document the substring-match approach in the class-level Javadoc as a deliberate choice — SSLCommerz does not document a stable error-code list; substring is the only durable hook. (If `ErrorCode` is missing any value referenced above, STOP and escalate — Wave C cannot add new codes.)

3. **Resilience wiring** already covered above. All outbound calls (initiate, queryStatus, refund) go through `adapterResilience.executeWithCircuitBreaker(Vendor.SSLCOMMERZ, callable)`. Do NOT call `circuitBreakerRegistry.circuitBreaker(...)` directly — the helper does that internally. A circuit-open state surfaces as `MfsAdapterException(VENDOR_DOWN, "Circuit open for SSLCOMMERZ")`, which `SslcommerzAdapter` catches at the method boundary and converts to `VendorResponse(FAILED, null, null, "circuit_open", VENDOR_DOWN)`.

4. **Catch-clause guidance.** Catching `RuntimeException` or `Exception` broadly is forbidden. Catch specific types:
   - `IOException` from `OkHttpClient.newCall().execute()` — map socket failures to the mapper; route `SocketTimeoutException` specifically to `VENDOR_DOWN`.
   - `JsonProcessingException` (Jackson) — vendor returned malformed JSON; map to `MFS_ADAPTER_FAILURE`.
   - `MfsAdapterException` from `AdapterResilience` — re-throw or convert to `VendorResponse(FAILED, ..., VENDOR_DOWN)` at the method boundary; do not wrap further.
   - All other exception types are programmer errors — let them propagate.

TESTS (target: 80% line, 70% branch on the `sslcommerz` package)

WireMock contract suite (`SslcommerzAdapterContractTest`) — extends Wave A's `VendorWireMockExtension`:
- `initiate_success_returnsGatewayPageURL` — stub `/gwprocess/v4/api.php` → 200 + `{"status":"SUCCESS","sessionkey":"abc123","GatewayPageURL":"https://sandbox.sslcommerz.com/..."}`. Assert `INITIATED` + non-null `redirectUrl`.
- `initiate_failed_mapsViaSubstring` — stub → `{"status":"FAILED","failedreason":"INSUFFICIENT BALANCE"}`. Assert `FAILED` + `INSUFFICIENT_FUNDS`.
- `initiate_validationError` — stub → `{"status":"FAILED","failedreason":"Invalid Card Number"}`. Assert `VALIDATION_ERROR`.
- `initiate_timeout_mapsToVendorDown` — fixed-delay > 10s on the stub. Assert `FAILED` + `VENDOR_DOWN`. Wall-clock < 12s.
- `queryStatus_validated_returnsCompleted` / `queryStatus_pending_returnsInitiated` / `queryStatus_cancelled_returnsCancelled` / `queryStatus_notFound_returnsResourceNotFound` (the `no_of_trans_found:"0"` case).
- `refund_success` — assert `COMPLETED`.
- `refund_sessionKeyOnly_doesValidationHopFirst` — stub `queryStatus` to return `bank_tran_id`, then refund. Assert `verify(1, getRequestedFor(... merchantTransIDvalidation ...))` AND `verify(1, postRequestedFor(... refund ...))`.
- `circuitBreakerOpen_failsClosedWithoutCallingVendor` — pre-trip breaker; assert `verify(0, ...)`.

Unit tests:
- `SslcommerzErrorMapperTest` — every documented substring maps as expected; case-insensitivity; null/empty handling; unmatched falls through to `MFS_ADAPTER_FAILURE`.
- `SslcommerzErrorMapperJqwikTest` — `forAll String reason` → `mapper.map(reason) != null` AND `mapper.map(reason) != ErrorCode.INTERNAL_ERROR`. Exhaustiveness invariant.

**No cross-adapter isolation test in this sub-prompt.** Cross-adapter isolation (Bkash slow + SSLCommerz fast and the reverse) is exercised in sub-prompt 12 (acceptance gate), where both adapters are guaranteed to be on the classpath. Adding it here would either skip silently on this branch (no coverage value) or violate the "touches only its sub-package" boundary. Sub-prompt 11's adapter must still pass its own single-vendor WireMock contract suite above; cross-vendor concurrency is the gate's job.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-adapters -am verify` BUILD SUCCESS.
- JaCoCo: sslcommerz package ≥ 80% line, ≥ 70% branch. Module aggregate stays ≥ 80%.
- `mvn -pl conflux-application -am verify` BUILD SUCCESS — no Modulith / ArchUnit regressions.
- gitleaks, Spotless, PMD, SpotBugs clean.
- Single commit (or up to two): `feat(adapters): SslcommerzAdapter + ErrorMapper (wave-c sub-prompt 11)`.

FORBIDDEN
- Implementing any other vendor.
- Implementing `VendorAuthClient` for SSLCommerz (it's intentionally absent — see step 1).
- Caching `storePasswd` anywhere, in any form.
- Editing `Vendor.java`, `ResilienceConfig`, `TokenService`, `PaymentProvider` (interface), or any locked port.
- Adding a new `ErrorCode`.
- Sharing the OkHttpClient with any other adapter (use `httpClientFactory.clientFor(Vendor.SSLCOMMERZ)` exclusively).
- Logging full response bodies (they include masked card data on the validator endpoint).
- Catching `RuntimeException` or `Exception` broadly — catch the specific types enumerated in deliverable 4.
- Shipping any cross-adapter isolation test in this sub-prompt — that's the acceptance gate's responsibility.

Output: file tree, the WireMock contract test names, JaCoCo tail for the sslcommerz package, one curl-style trace of the `initiate` happy path (request + response with `store_passwd` MASKED per the redaction rules above, session/`val_id` masked as `<first4>***`) demonstrating the form-encoded body and the `Content-Type` header.
```
