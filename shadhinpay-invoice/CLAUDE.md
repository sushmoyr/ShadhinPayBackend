# Invoice Management — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/invoice/PRD.md
4. DOCS/features/invoice/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes merchant invoice CRUD and the public slug-based payment page)

## Module scope
Merchant-facing invoice issuance and the customer-facing public payment page. Owns `Invoice` (with cryptographically unguessable slug, optional expiry) and `InvoiceItem`. Initiates payments by delegating to `payment-core` via its use-case interface; reacts to lifecycle events to mark invoices `PAID`. Hosts the hourly expiry job and QR-code generation.

## Allowed dependencies
- shadhinpay-common (read-only)
- Cross-module use-case interfaces consumed:
  - `payment-core.InitiatePaymentUseCase`
- Publishes (events): none.
- Consumes (events): `PaymentCompletedEvent` (filtered on `metadata.invoice_id`).
- Exposes (use-case interfaces, called by other modules): none.

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
3. Integration test for the consumed `PaymentCompletedEvent` listener (matching and non-matching `invoice_id` paths).
4. Property tests (jqwik) for the **slug-uniqueness invariant** below — 1M generated slugs must produce zero collisions.
5. WireMock contract tests for any external HTTP integration (none expected; QR generation is in-process).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Slugs must be cryptographically unguessable** — `SecureRandom`-backed URL-safe Base64 of ≥ 16 bytes (`sp_inv_` prefix + entropy). Do not use sequential IDs or short hashes; the public URL is the only guard against enumeration of merchant invoices.
- **Expiry job runs hourly** and transitions `INITIATED`/`VIEWED` invoices to `EXPIRED` when `Instant.now() > expiresAt`. The `InitiateInvoicePaymentUseCase` must re-check `expiresAt` before delegating to `payment-core` — racing the cron is real, and an expired invoice paying through is a customer-facing bug.
- **Public page is a controlled surface:** only `Business.displayName` and `Business.logoUrl` may be exposed (resolved indirectly via the data carried on the invoice or via the public read of provisioning info). No merchant credentials, no merchant phone, no internal IDs. Rate-limit the public endpoint by IP.
- **Event correlation:** `PaymentCompletedEvent` carries a generic `metadata` map; the listener filters on `metadata.invoice_id`. Events without that key are not yours — return immediately. Update the linked invoice's status atomically; do not assume the invoice still exists (it may have been cancelled).
- **Delegation, not duplication:** the `InitiateInvoicePaymentUseCase` calls `payment-core.InitiatePaymentUseCase`. Do not re-implement quota/risk/idempotency here — that belongs in `payment-core`.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
