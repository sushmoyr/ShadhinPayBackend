# Phase 1 Wave B — `payment-core` module prompts

> **Branch:** `phase-1/payment-core` — run all three sub-prompts sequentially in the same git worktree on the same branch.
> **Prerequisite:** `phase-1/provisioning` must be merged to `main` before 8a starts. `GetBusinessByApiKeyUseCaseImpl`, `GetVendorConfigUseCaseImpl`, and `CredentialsResolverImpl` must be autowire-able.
> **Scope:** ship `Transaction`, `WebhookOutbox`, `IdempotencyRecord` entities; the three inbound use cases (`InitiatePaymentUseCase`, `ProcessVendorCallbackUseCase`, `HandleWebhookRetryUseCase`); the orchestration logic (idempotency → provisioning → risk → quota → persist → adapter dispatch); the public REST controllers (`/v1/payments`, `/v1/payments/{id}`, `/v1/payments/{id}/refund`); the webhook outbox dispatcher; the reconciliation scheduler for `PENDING_RECOVERY`.
> **Read first (every sub-prompt):** the [Wave B index](../PHASE_1_WAVE_B_PROMPTS.md) — cross-cutting decisions; `conflux-payment-core/CLAUDE.md`; `DOCS/features/payment-core/PRD.md`; `DOCS/features/payment-core/TECH_SPEC.md`; `PHASE_1_WAVE_A_REPORT.md` § "Locked Wave A Contracts".

Sub-prompts:
1. [8a — schema + `InitiatePaymentUseCaseImpl` + public REST controllers](#prompt-8a--payment-core-schema--initiatepaymentusecaseimpl--public-rest-controllers)
2. [8b — vendor-callback + webhook outbox dispatcher + reconciliation scheduler](#prompt-8b--payment-core-vendor-callback--webhook-outbox-dispatcher--reconciliation-scheduler)
3. [8c — concurrency + property tests + Modulith replay + coverage push](#prompt-8c--payment-core-concurrency--property-tests--modulith-replay--coverage-push)

---

## Prompt 8a — payment-core schema + InitiatePaymentUseCaseImpl + public REST controllers

```
You are starting the `conflux-payment-core` Wave B module on branch `phase-1/payment-core`. This is the FIRST of THREE sub-prompts (8a → 8b → 8c). `phase-1/provisioning` IS ALREADY MERGED TO MAIN — if you don't see the provisioning impls on the branch base, stop.

Your sub-scope (8a): persistence (Transaction, WebhookOutbox, IdempotencyRecord), `InitiatePaymentUseCaseImpl`, the public REST controllers for payment initiation and status query, migration V1014.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_B_PROMPTS.md "Cross-cutting decisions" (especially #5 idempotency scope and #6 PARTNER vs CUSTOM)
- conflux-payment-core/CLAUDE.md
- DOCS/features/payment-core/PRD.md (full)
- DOCS/features/payment-core/TECH_SPEC.md (full)
- conflux-payment-core/src/main/java/pay/conflux/backend/paymentcore/usecase/ (locked interfaces + DTOs — InitiatePaymentRequest, PaymentInitiationResult, InitiatePaymentUseCase)
- conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/usecase/ (all the impls you'll inject — BusinessContext, VendorConfigDescriptor, CredentialsResolver)
- conflux-risk/src/main/java/pay/conflux/backend/risk/usecase/EvaluateTransactionUseCase.java
- conflux-quota/src/main/java/pay/conflux/backend/quota/usecase/{Reserve,Confirm,Release}QuotaUseCase.java
- conflux-ledger/src/main/java/pay/conflux/backend/ledger/usecase/RecordJournalEntryUseCase.java
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/support/PaymentProviderRegistry.java
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/ (all port types — PaymentProvider, VendorPaymentRequest, VendorResponse, VendorCredentials, VendorStatus)

WORK ONLY IN
- conflux-payment-core/src/main/java/pay/conflux/backend/paymentcore/{entity,repository,dto,mapper,usecase/impl,controller,constant,validator,spec}/...
- conflux-payment-core/src/main/resources/db/migration/...
- conflux-payment-core/src/test/...
- conflux-application/src/test/java/pay/conflux/backend/paymentcore/... (integration tests live here when they touch multiple modules)

DO NOT TOUCH
- conflux-common/, identity, ledger, risk, quota, adapters, provisioning.
- Root pom.xml.
- DOCS/contracts/openapi.json.
- Existing Wave A or 7a/7b migrations.
- The locked InitiatePaymentUseCase interface or its DTOs.

DELIVERABLES

1. **Flyway migration `V1014__payment_core_schema.sql`** — three tables:
   - `transactions` — `id UUID PK`, `business_id UUID NOT NULL REFERENCES businesses(id)`, `merchant_id UUID NOT NULL`, `amount_value NUMERIC(19,4) NOT NULL`, `amount_currency CHAR(3) NOT NULL DEFAULT 'BDT'`, `status TEXT NOT NULL CHECK (status IN ('INITIATED','PENDING','COMPLETED','FAILED','CANCELLED','PENDING_RECOVERY','PENDING_RISK'))`, `vendor TEXT NOT NULL`, `mode TEXT NOT NULL CHECK (mode IN ('PARTNER','CUSTOM'))`, `merchant_order_reference TEXT NOT NULL`, `vendor_transaction_id TEXT`, `metadata JSONB`, `callback_url TEXT`, `webhook_url TEXT`, `retry_count INT NOT NULL DEFAULT 0`, `version BIGINT NOT NULL DEFAULT 0` (optimistic locking), `created_at`, `updated_at`. Indexes on `(business_id, created_at DESC)`, `(status)` (partial: `WHERE status IN ('PENDING_RECOVERY', 'PENDING')`), `(vendor_transaction_id)`.
   - `webhook_outbox` — `id UUID PK`, `transaction_id UUID NOT NULL REFERENCES transactions(id)`, `business_id UUID NOT NULL`, `event_type TEXT NOT NULL` (`PAYMENT_INITIATED` | `PAYMENT_COMPLETED` | `PAYMENT_FAILED` | `PAYMENT_REFUNDED` | `WEBHOOK_PING`), `payload JSONB NOT NULL`, `status TEXT NOT NULL CHECK (status IN ('PENDING','SENT','FAILED'))`, `attempt_count INT NOT NULL DEFAULT 0`, `next_attempt_at TIMESTAMPTZ NOT NULL`, `last_error TEXT`, `created_at`, `updated_at`. Index on `(status, next_attempt_at)` for the dispatcher poll.
   - `idempotency_records` — `business_id UUID NOT NULL`, `request_key TEXT NOT NULL`, `response_payload JSONB NOT NULL`, `transaction_id UUID NOT NULL`, `expires_at TIMESTAMPTZ NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, PRIMARY KEY `(business_id, request_key)`. Index on `expires_at` for cleanup.

2. **Entities** (`pay.conflux.backend.paymentcore.entity`): `Transaction`, `WebhookOutbox`, `IdempotencyRecord`. `@Version` on `Transaction.version`. `@Enumerated(EnumType.STRING)` for `status`, `mode`, `event_type`. Compound `@IdClass` or `@EmbeddedId` for `IdempotencyRecord` composite PK. **No `@Data`.**

3. **Repositories**:
   - `TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction>` — `findByVendorTransactionId(String)`, `findAllByStatusAndUpdatedAtBefore(Status, Instant)` (for reconciliation poll).
   - `WebhookOutboxRepository` — `findAllByStatusAndNextAttemptAtBefore(Status, Instant, Pageable)` (dispatcher pull).
   - `IdempotencyRecordRepository` — `findByBusinessIdAndRequestKey(UUID, String)`, `deleteAllByExpiresAtBefore(Instant)` (cleanup job).

4. **DTOs**:
   - Request: `InitiatePaymentRestRequest(@NotNull @DecimalMin("0.0001") BigDecimal amount, @NotBlank @Pattern("^[A-Z]{3}$") String currency, @NotBlank String vendor, @NotBlank String merchantOrderReference, @URL String callbackUrl, @URL String webhookUrl, Map<String,String> metadata)`. (The X-Idempotency-Key header is read separately at the controller layer.)
   - Response: `PaymentResponseDto(UUID transactionId, String status, String redirectUrl, BigDecimal amount, String currency, String vendor, String merchantOrderReference, Instant createdAt)`.
   - List/summary: `TransactionSummaryDto`.

5. **`InitiatePaymentUseCaseImpl`** (`pay.conflux.backend.paymentcore.usecase.impl`):
   - `@UseCase`. `@Transactional`. Orchestration order (NO DEVIATION):
     1. **Idempotency check** — `IdempotencyRecordRepository.findByBusinessIdAndRequestKey(businessId, idempotencyKey)`. On hit and not expired: return the cached `PaymentInitiationResult` (deserialize from `responsePayload`). Done. Also check Redis as L1 cache (key `idempotency:{businessId}:{requestKey}`, TTL 24h) — DB is L2 for resilience.
     2. **Provisioning lookup** — DO NOT call `GetBusinessByApiKeyUseCase` here (that already ran at the gateway filter; the resolved `businessId` is in the request). Call `GetVendorConfigUseCase.execute(businessId, vendor)` to get the `VendorConfigDescriptor`. From it, read `mode` (PARTNER or CUSTOM).
     3. **Risk evaluation** — call `EvaluateTransactionUseCase.evaluate(new TransactionContext(...))` with `merchantId`, `businessId`, `amount`, `vendor`, `metadata`. **Fail-CLOSED**: any thrown exception → re-throw as `InvalidOperationStateException` with `ErrorCode.RISK_REJECTED`. On `Action.BLOCK`: throw the same. On `Action.FLAG`: persist `Transaction.status = PENDING_RISK` and return `PaymentInitiationResult(transactionId, "", "PENDING_RISK")` — no MFS dispatch. On `Action.ALLOW`: continue.
     4. **Quota reservation** (PARTNER mode only) — call `ReserveQuotaUseCase.execute(merchantId)`. **Fail-OPEN**: per Wave A contract, the impl returns `Status.FREE` on Redis outage and never throws — but defensive catch: any `RuntimeException` → log WARN, treat as `Status.FREE`. Save `reservationId` for confirm/release later. CUSTOM mode skips this entirely (no `reservationId`).
     5. **Persist Transaction** — status `INITIATED`. Save `Transaction`, capture the generated id.
     6. **Adapter dispatch** — `PaymentProviderRegistry.lookup(Vendor.valueOf(vendor.toUpperCase()))` to get the `PaymentProvider`. Build a `VendorCredentials` via `CredentialsResolver.resolveCredentials(businessId, vendor)`. Build a `VendorPaymentRequest` (transactionId, amount, currency, vendor, mode, merchantOrderReference, callbackUrl, metadata). Call `provider.initiate(req, creds)`.
     7. **State transition** — read the `VendorResponse`. On `INITIATED` (vendor returned a redirect): update `Transaction.status = PENDING`, store `vendorTransactionId`, persist. On `COMPLETED` (synchronous success, rare for MFS but possible for mock/stripe): set status to `COMPLETED`, **confirm quota** if reservationId, **record journal entry** via `RecordJournalEntryUseCase`, publish `PaymentCompletedEvent`. On `FAILED`/`CANCELLED`: set status accordingly, **release quota** if reservationId, publish `PaymentFailedEvent` or set `CANCELLED`. On any thrown exception during adapter call: set status to `PENDING_RECOVERY`, **leave the quota reservation alone** (the reconciliation poller in 8b will resolve it). Build `PaymentInitiationResult`.
     8. **Publish `PaymentInitiatedEvent`** — Spring Modulith publisher; same transaction.
     9. **Enqueue webhook outbox** — insert a `WebhookOutbox` row with the initial event type (`PAYMENT_INITIATED`); same transaction. `next_attempt_at = now()` so the dispatcher picks it up immediately.
     10. **Cache idempotency** — persist `IdempotencyRecord` with `responsePayload = JSON.toJson(result)`, `expiresAt = now() + 24h`. Same transaction. Also write to Redis L1.
     11. Return `result`.

   - **All exceptions before step 5 (persistence) must leave NO database side effects.** The orchestrator must roll back the transaction. After step 5, the `Transaction` row stays — its terminal status reflects what happened (`PENDING_RECOVERY` for adapter exceptions, `PENDING` for redirect, etc.).

6. **Public REST controllers**:
   - `PaymentCoreRoutes` constants: `PAYMENTS = "/api/v1/payments"`, `PAYMENT_BY_ID = "/api/v1/payments/{id}"`, `PAYMENT_REFUND = "/api/v1/payments/{id}/refund"`, `PAYMENT_CALLBACK = "/api/v1/payments/callback/{vendor}"` (the public-facing return URL from the MFS — 8b will implement the controller method, 8a defines the route).
   - `MerchantPaymentController` interface + `Impl`:
     - `POST /api/v1/payments` — header `X-Idempotency-Key` (required), header `X-Business-Id` (resolved by the global API-key filter — Wave B leaves the filter wiring to 8c; for 8a's controller, accept the header as input and trust it). Calls `InitiatePaymentUseCase`. Returns `ApiResult.created(PaymentResponseDto)` with HTTP 201. Auth: `hasAuthority('MERCHANT')` (filter-provided).
     - `GET /api/v1/payments/{id}` — returns `PaymentResponseDto`. Tenant check: `transaction.businessId == header X-Business-Id`. 403 otherwise.
   - `MerchantPaymentRefundController` — 8a stubs this (`POST /api/v1/payments/{id}/refund` returns 501 `NOT_IMPLEMENTED`). 8b implements it.

TESTS (target: 55% module coverage on this sub-prompt)

Unit:
- `InitiatePaymentUseCaseImpl` (with Mockito):
  - Idempotency hit returns cached result, no downstream calls.
  - Risk `BLOCK` → throws `InvalidOperationStateException(RISK_REJECTED)`, no `Transaction` persisted.
  - Risk `FLAG` → `Transaction.status = PENDING_RISK`, no adapter call.
  - Risk throws → fail-CLOSED, no `Transaction` persisted.
  - Quota throws → fail-OPEN (log + continue), `Transaction` proceeds.
  - PARTNER mode reserves quota; CUSTOM mode skips.
  - Adapter `INITIATED` response → `Transaction.status = PENDING`, `vendorTransactionId` stored.
  - Adapter `COMPLETED` response → quota confirmed + journal entry recorded + `PaymentCompletedEvent` published.
  - Adapter `FAILED` response → quota released + `PaymentFailedEvent` published.
  - Adapter throws → `Transaction.status = PENDING_RECOVERY`, quota reservation NOT released.
  - Webhook outbox row inserted in the same transaction.
  - Idempotency record persisted with 24h TTL.

Slice (`@WebMvcTest`):
- `MerchantPaymentControllerImpl` POST happy path returns 201 with envelope.
- Missing `X-Idempotency-Key` → 400 `VALIDATION_ERROR`.
- Missing `X-Business-Id` → 401 (the filter would have rejected; for the slice test, simulate).

Integration (`@SpringBootTest` + Testcontainers Postgres + Redis):
- Full orchestration with `MockAdapter` + `mock_outcome=success`: 201 returned, Transaction row exists, WebhookOutbox row queued, IdempotencyRecord row exists.
- Replay (same idempotency key) returns the exact same response, exactly one Transaction row.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-payment-core -am verify` BUILD SUCCESS.
- JaCoCo ≥ 55%.
- ArchUnit + Modulith green. The new module's modulith doc shows `InitiatePaymentUseCaseImpl` inside the module with no leakage.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(payment-core): schema + InitiatePaymentUseCaseImpl + public REST (8a)`.

FORBIDDEN
- Implementing `ProcessVendorCallbackUseCase`, `HandleWebhookRetryUseCase`, or the reconciliation scheduler (8b).
- Implementing the webhook outbox dispatcher (8b).
- Implementing refund (8b).
- Bypassing the orchestration order. The order is the contract.
- Logging credentials, the resolved `VendorCredentials.values()` map, or the X-Idempotency-Key.
- Catching `RiskEngineException` and downgrading it (fail-CLOSED is non-negotiable).
- Modifying any locked contract.

Output: file tree, orchestration trace from a successful integration-test run, JaCoCo tail.
```

---

## Prompt 8b — payment-core vendor-callback + webhook outbox dispatcher + reconciliation scheduler

```
You are continuing the `conflux-payment-core` Wave B module on branch `phase-1/payment-core`. Sub-prompt 8a is committed. Your sub-scope (8b): `ProcessVendorCallbackUseCase` (the customer-return handler), the webhook outbox dispatcher (the `WebhookOutbox` drainer), the reconciliation scheduler (resolves `PENDING_RECOVERY`), refund support, and the public vendor-callback route handler.

READ FIRST
- The 8a commits
- DOCS/features/payment-core/TECH_SPEC.md §4.2 (recovery logic), §4.3 (webhook retry policy)
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/port/PaymentProvider.java (queryStatus, refund methods)

DELIVERABLES

1. **`ProcessVendorCallbackUseCase` + Impl** (`pay.conflux.backend.paymentcore.usecase`):
   - Interface: `ProcessVendorCallbackResult execute(String vendor, Map<String, String> callbackParams)`.
   - Impl: identifies the `Transaction` from `callbackParams` (vendor-specific — for MOCK, `mock_trx_id`). Resolves the adapter via the registry. Calls `provider.queryStatus(vendorTrxId, creds)` to get authoritative status. Transitions:
     - `COMPLETED` → `Transaction.status = COMPLETED`, confirm quota, record journal entry, publish `PaymentCompletedEvent`, enqueue `PAYMENT_COMPLETED` webhook.
     - `FAILED` → `status = FAILED`, release quota, publish `PaymentFailedEvent`, enqueue `PAYMENT_FAILED` webhook.
     - `CANCELLED` → `status = CANCELLED`, release quota, enqueue `PAYMENT_FAILED` webhook (one webhook event covers both).
     - `INITIATED` (still pending) → no state change, no events.
     - On `provider.queryStatus` exception or vendor timeout → status `PENDING_RECOVERY`, no events, no webhook (reconciliation scheduler will retry).
   - **Idempotent**: re-running the same callback for an already-`COMPLETED` transaction is a no-op (no duplicate events, no duplicate journal entries).
   - **`@Retryable`** on the use-case method with `OptimisticLockException` retry policy (5 attempts, exponential backoff) — for the version-column collision case.

2. **Public vendor-callback controller method** (in `MerchantPaymentController` from 8a or a new `VendorCallbackController`):
   - `POST /api/v1/payments/callback/{vendor}` — accepts vendor-specific params as a `Map<String, String>` (form-urlencoded or JSON, vendor-dependent — for MOCK, JSON body `{"mock_trx_id": "..."}`). NO auth (vendors don't sign in). Vendor signature verification belongs to the adapter — for MOCK, no verification.
   - Calls `ProcessVendorCallbackUseCase`. Returns `ApiResult.ok(...)` with the resulting payment status; this response is what the vendor reads.

3. **`HandleWebhookRetryUseCase` + Impl**:
   - Scheduled component `WebhookOutboxDispatcher`:
     - `@Scheduled(fixedDelay = 5000)` — every 5 seconds, polls `WebhookOutboxRepository.findAllByStatusAndNextAttemptAtBefore(PENDING, now(), PageRequest.of(0, 50))`.
     - For each: resolve `Business.webhookUrl` via direct repository (you may inject `BusinessRepository` here — but **only** to read `webhookUrl` and `webhookSecretEncrypted` for that businessId; **do NOT do general cross-feature reads**, only this hot-path lookup; document the exception in the commit message). Decrypt the secret via `EncryptionService`.
     - Build HMAC-SHA256 signature over the payload using the merchant's webhook secret.
     - HTTP POST to `webhookUrl` with `X-PGW-Signature` header. OkHttpClient, 5s connect / 10s read timeout. Isolated thread pool (`@Async("webhookExecutor")` with a dedicated `ThreadPoolTaskExecutor` bean — separate from `asyncEventExecutor`).
     - On 2xx: `outbox.status = SENT`, `lastError = null`.
     - On non-2xx or exception: `outbox.attempt_count++`, `lastError = msg`, `nextAttemptAt = now() + backoff[attempt_count]`. Backoff schedule: `[1m, 5m, 15m, 1h, 6h, 24h]`. After attempt 6: `status = FAILED`.
   - **Never logs the webhook secret or the signed payload.** Logs only `(businessId, transactionId, eventType, statusCode, attemptCount)`.

4. **`ReconciliationScheduler`** (`pay.conflux.backend.paymentcore.scheduler`):
   - `@Scheduled(fixedDelay = 30000)` — every 30 seconds, polls `TransactionRepository.findAllByStatusAndUpdatedAtBefore(PENDING_RECOVERY, now().minusSeconds(60), PageRequest.of(0, 100))`.
   - For each: re-invoke `ProcessVendorCallbackUseCase.execute(vendor, Map.of("mock_trx_id", vendorTrxId))` (or the equivalent generic "queryStatus" path — refactor 8b's `ProcessVendorCallbackUseCase` to expose a `resolveByTransactionId(UUID)` helper if needed).
   - After 24 hours in `PENDING_RECOVERY` without resolution: mark as `FAILED` with a `reconciliation_timeout` flag in `metadata`. This is the **only** path that finalizes `PENDING_RECOVERY` to `FAILED`.

5. **Refund** — `RefundPaymentUseCase` + Impl. Wire `MerchantPaymentRefundController`'s 501 stub from 8a to call it. Implements vendor `refund(...)` adapter call, persists a new `Transaction` row for the refund with status mirroring the vendor response, publishes `PaymentRefundedEvent`, enqueues `PAYMENT_REFUNDED` webhook.

6. **Idempotency cleanup job** — `@Scheduled(cron = "0 0 * * * *")` — hourly, runs `IdempotencyRecordRepository.deleteAllByExpiresAtBefore(now())`.

TESTS (cumulative target: 70% module coverage)

Unit:
- `ProcessVendorCallbackUseCaseImpl`:
  - `COMPLETED` → status update, quota confirm, journal entry, event published.
  - `FAILED` → status update, quota release, event published.
  - Idempotent re-callback for already-`COMPLETED` → no-op (verify with `verify(eventPublisher, times(1))` across two calls).
- `WebhookOutboxDispatcher`:
  - Happy path: 2xx response → `SENT`.
  - 500 response → attempt count incremented, `nextAttemptAt` advanced by backoff.
  - After 6 attempts → `FAILED`.
  - HMAC signature is byte-identical for the same payload + secret (regression).
  - Slow merchant (10s response) doesn't block other webhook dispatches (verify with concurrent test: 10 webhooks to a slow URL + 10 to a fast URL, fast ones finish first).
- `ReconciliationScheduler`:
  - `PENDING_RECOVERY` transaction polled → status updated based on `queryStatus`.
  - 24h-old `PENDING_RECOVERY` → finalized as `FAILED` with `reconciliation_timeout` flag.

Integration (`@SpringBootTest` + Testcontainers + WireMock):
- End-to-end refund flow with `MockAdapter`.
- Webhook delivery to a WireMock endpoint: assert exactly 1 POST with the correct HMAC signature.
- Webhook retry: WireMock returns 500 on attempt 1, 200 on attempt 2 (with backoff override via `@DynamicPropertySource` to make tests fast). Assert outbox row is `SENT` after attempt 2.

ACCEPTANCE CRITERIA (this sub-prompt)
- All 8a criteria still hold.
- JaCoCo ≥ 70%.
- Webhook dispatcher uses an isolated `ThreadPoolTaskExecutor` named `webhookExecutor`.
- No webhook secret appears in any log line (greppable assertion in tests).
- Commit: `feat(payment-core): vendor-callback + webhook dispatcher + reconciliation (8b)`.

FORBIDDEN
- Inline HTTP calls in `InitiatePaymentUseCaseImpl` or the controllers — every outbound webhook goes through the outbox.
- Using `asyncEventExecutor` for webhooks (must be isolated).
- Logging webhook secrets or full request bodies that include them.
- Finalizing `PENDING_RECOVERY` to `FAILED` anywhere except the 24h-timeout path in `ReconciliationScheduler`.
- Modifying any locked contract.

Output: webhook-delivery trace (HMAC signature, status code, attempt count), reconciliation log showing a `PENDING_RECOVERY` → `COMPLETED` transition, JaCoCo tail.
```

---

## Prompt 8c — payment-core concurrency + property tests + Modulith replay + coverage push

```
You are completing the `conflux-payment-core` Wave B module on branch `phase-1/payment-core`. Sub-prompts 8a and 8b are committed. Your sub-scope (8c): concurrency tests, jqwik property tests, the Modulith replay test that proves event redelivery after a downstream-module crash, the global API-key filter that bridges provisioning + payment-core, and the final coverage push to ≥ 80%.

READ FIRST
- The 8a + 8b commits
- `conflux-quota/src/test/.../QuotaConcurrencyTest.java` and `conflux-ledger/src/test/.../LedgerConcurrencyIT.java` for the 100-thread CountDownLatch pattern Wave A already established
- Spring Modulith docs for `IncompleteEventPublications` (Context7 MCP if available)

DELIVERABLES

1. **Global API-key filter** (`conflux-application/src/main/java/pay/conflux/backend/application/security/ApiKeyAuthFilter.java`):
   - `OncePerRequestFilter`. Reads `Authorization: Bearer <key>` (or `X-API-Key` header — accept both). Calls `provisioning.GetBusinessByApiKeyUseCase`. On success: populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken(businessId, null, List.of(new SimpleGrantedAuthority("MERCHANT")))`. Sets request attribute `X-Business-Id` (or directly populates a `RequestAttributes` slot that `MerchantPaymentControllerImpl` reads instead of the header — choose one; the controller from 8a expected a header, so set `request.setAttribute(...)` and have the controller read the attribute via `@RequestAttribute`).
   - On miss/revoked: returns 401 with `ApiResult.error(UNAUTHORIZED, "Invalid API key")`.
   - Whitelisted routes (no auth required): the public vendor-callback endpoint, `/actuator/health`, `/v3/api-docs/**`.

2. **Concurrency test** (`conflux-application/src/test/java/pay/conflux/backend/paymentcore/PaymentInitiationConcurrencyIT.java`):
   - `@SpringBootTest` + Testcontainers Postgres + Redis + Wave A's existing harness.
   - 100 threads, same `(businessId, idempotencyKey)`. Each calls `InitiatePaymentUseCase.execute(...)`. After `CountDownLatch.await()`:
     - Exactly 1 `Transaction` row exists for that idempotency key.
     - Exactly 1 `IdempotencyRecord` row exists.
     - All 100 thread results are byte-identical (same `transactionId`, same `redirectUrl`, same `status`).
   - Time budget: 60s wall-clock.

3. **jqwik property tests**:
   - `IdempotencyInvariantProperty` — for any sequence of N (1..50) parallel calls with the same `(businessId, key)`, the count of `Transaction` rows is exactly 1.
   - `OrchestrationRollbackProperty` — for any call that fails at risk evaluation, the `Transaction`, `IdempotencyRecord`, and `WebhookOutbox` tables remain unchanged from the pre-call state.
   - `WebhookSignatureProperty` — for any payload and secret, `verify(payload, signature) == true` and `verify(tamperedPayload, signature) == false`.

4. **Modulith replay test** (`PaymentCoreModulithReplayIT`):
   - `@SpringBootTest`. Initiate a successful payment that publishes `PaymentCompletedEvent`. Simulate a ledger-side listener crash by configuring a test bean that throws on first delivery. Verify that the `event_publication` table has an unprocessed entry. Trigger replay via `IncompleteEventPublications.resubmitIncompletePublications(...)`. Verify the ledger received the event on retry and the journal entry was recorded.

5. **Coverage push to ≥ 80%**:
   - Controller slice tests for every public route (auth matrix, validation, idempotency).
   - Tenant isolation tests: a transaction owned by Business A is not visible to API key for Business B (`GET /api/v1/payments/{id}` returns 404 or 403; pick 404 to avoid information disclosure).

6. **OpenAPI tags**: add `@Tag(name = "Payments - Merchant")` on `MerchantPaymentController`, `@Tag(name = "Payments - Callback")` on the vendor-callback controller. DO NOT commit `DOCS/contracts/openapi.json`.

TESTS — see deliverables 2, 3, 4, 5.

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All 8a + 8b criteria still hold.
- JaCoCo ≥ 80%.
- Concurrency test: 100 threads same key → exactly 1 Transaction. Logged as proof in the test output.
- Modulith replay test green.
- jqwik trial counts ≥ 500 each.
- The API-key filter is wired into `SecurityConfig`; an unauthenticated `POST /api/v1/payments` returns 401, an authenticated one returns 201.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(payment-core): concurrency + jqwik + modulith replay + coverage push (8c) — closes payment-core`.

FORBIDDEN
- Modifying any locked Wave A or Wave B contract.
- Editing `DOCS/contracts/openapi.json`.
- Loosening the idempotency invariant (it's the load-bearing guarantee for the payments API).
- Returning more than 1 Transaction in the concurrency test under any code path.

Output: concurrency-test result (`countDownLatch.await()` finished, `Transaction.count == 1`), modulith replay log, jqwik trial counts, final JaCoCo per-class table.
```
