# Payment Core — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/payment-core/PRD.md
4. DOCS/features/payment-core/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes the public payment-initiation, vendor-callback, and refund endpoints)

## Module scope
The orchestrator. Owns `Transaction`, `WebhookOutbox`, and `IdempotencyRecord`. For each inbound payment: validates idempotency, resolves business + vendor config, evaluates risk, reserves quota, persists the `Transaction`, dispatches to the appropriate MFS adapter, and (on completion) records the journal entry, publishes the lifecycle event, and enqueues the merchant webhook for reliable delivery.

## Allowed dependencies
- shadhinpay-common (read-only)
- Cross-module use-case interfaces consumed:
  - `provisioning.GetBusinessByApiKeyUseCase`
  - `provisioning.GetVendorConfigUseCase`
  - `risk.EvaluateTransactionUseCase`
  - `quota.ReserveQuotaUseCase` / `ConfirmQuotaUseCase` / `ReleaseQuotaUseCase`
  - `ledger.RecordJournalEntryUseCase`
- Adapter port: `adapters.PaymentProvider` (and the `MockAdapter` implementation in Wave A; real adapters land in Wave C and are wired transparently via `supports(Vendor)`).
- Redis (via `common`'s cache abstraction) — required for idempotency-key caching.
- Publishes (events): `PaymentInitiatedEvent`, `PaymentCompletedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent` (carry a generic `metadata` map).
- Consumes (events): none.
- Exposes (use-case interfaces, called by other modules): `InitiatePaymentUseCase` (called by `invoice`).

## Forbidden
- Reaching into another feature's `repository`, `entity`, or `mapper` packages.
- Modifying `shadhinpay-common`, the cross-module contracts, or any other feature module.
- Skipping the global `ApiResult<T>` envelope.
- SQL triggers for createdAt/updatedAt — use `@CreationTimestamp`/`@UpdateTimestamp`.
- Storing plaintext credentials, password hashes, or PII without encryption.
- Field injection (`@Autowired` on fields). Constructor injection only.
- `@Data` on JPA entities.
- `EnumType.ORDINAL`.

## Definition of done
1. Every use case listed in TECH_SPEC §3 is implemented and unit-tested.
2. JaCoCo line coverage ≥ 80% for this module.
3. Integration test for every published Modulith event (`PaymentInitiatedEvent`, `PaymentCompletedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent`) including a Modulith replay test that proves a downstream-module crash mid-event causes redelivery, not loss.
4. Property tests (jqwik) for the **idempotency invariant** below and the orchestration-rollback invariants (a failed risk/quota call leaves no `Transaction` and no reserved quota).
5. **WireMock contract tests** wiring the `MockAdapter` end-to-end (vendor success, vendor 5xx, vendor timeout → `PENDING_RECOVERY`).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Idempotency on `(merchantId, X-Idempotency-Key)` for 24 h.** Concurrent requests sharing a key must produce *exactly one* `Transaction` and one cached response — verify with a 100-thread `CountDownLatch` test (Phase 2 §5.1 will run this against the real merge train, but the test must already exist here).
- **Webhook signing with HMAC-SHA256** using the merchant's `webhookSecret` (resolved via `provisioning`). Signature in the `X-ShadhinPay-Signature` header. Backoff schedule: 1 m → 5 m → 15 m → 1 h → 6 h → 24 h. The `WebhookOutbox` is enqueued *in the same DB transaction* as the `Transaction.status` update, then drained by an isolated thread pool — never an inline HTTP call.
- **`PENDING_RECOVERY` is a real state, not an error code.** On vendor timeout, the transaction lands in `PENDING_RECOVERY` and the reconciliation poller calls `queryStatus` until the vendor gives a definitive answer. Never finalize as `FAILED` on a timeout — that's a finance reconciliation incident waiting to happen.
- **PARTNER vs CUSTOM mode routing:** PARTNER uses platform credentials and meters quota; CUSTOM uses the merchant's own `VendorConfig.credentials` and skips quota entirely. The mode flows from `provisioning` — never default it locally.
- **Pre-flight order matters:** idempotency check → provisioning lookup → risk eval (fail-closed) → quota reserve (fail-open) → persist → adapter dispatch. If risk returns `FLAG`, the transaction goes to `PENDING_RISK` and is *not* dispatched. If quota throws (Redis down), proceed; if risk throws, block.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
