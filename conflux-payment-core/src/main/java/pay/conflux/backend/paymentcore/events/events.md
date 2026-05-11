# Payment Core Module — Outbound Events

This package is the published `events` named-interface for the `payment-core` module.
All events are immutable Java records and are dispatched via the Spring Modulith
JDBC Event Publication Registry (see `conflux-common` TECH_SPEC §5).

Every event carries `traceId` so consumers can re-establish MDC context across
the `@TransactionalEventListener(phase = AFTER_COMMIT)` boundary, and `occurredAt`
for downstream audit trails.

## `PaymentInitiatedEvent`
**Signals:** A `Transaction` has been persisted with status `INITIATED` and the merchant has been handed a vendor redirect URL.
**Fires from:** `InitiatePaymentUseCase`, after pre-flight (quota, risk) and inside the same DB transaction that creates the row.
**Current consumers:** `risk` (records pre-flight signals against the live transaction), `quota` (decrements free-tier reservation in PARTNER mode). Ledger does **not** post yet — it waits for `PaymentCompletedEvent`.

## `PaymentCompletedEvent`
**Signals:** The vendor has confirmed a successful debit; `Transaction.status = COMPLETED`. This is the canonical "money moved" signal.
**Fires from:** `ProcessVendorCallbackUseCase` (and the recovery scheduler) on definitive vendor success, inside the transaction that flips status.
**Metadata carrier:** When the originating request came from an invoice, `metadata["invoice_id"]` is populated so `invoice` can mark the invoice `PAID`.
**Current consumers:** `ledger` (Debit `ESCROW`, Credit `MERCHANT_PAYABLE` + `PLATFORM_REVENUE` — uses `(sourceType, sourceId)` for idempotency), `invoice` (flips invoice → `PAID` when `invoice_id` is present), `settlement` (rolls the merchant payable forward).

## `PaymentFailedEvent`
**Signals:** A transaction reached a definitive failure state — `Transaction.status = FAILED` (vendor rejection, terminal timeout after recovery, etc.).
**Fires from:** `ProcessVendorCallbackUseCase` on vendor failure response and the reconciliation scheduler when `PENDING_RECOVERY` resolves to failure.
**Current consumers:** `quota` (refunds the reservation made at initiation), `risk` (feeds failure rates into scoring), `invoice` (flips linked invoice → `FAILED` when `invoice_id` is present in metadata). Ledger does **not** post for failures.

## `PaymentRefundedEvent`
**Signals:** A refund has been issued against a previously-completed transaction. The refund itself is a separate `Transaction` (`transactionId`) tied to the original via `originalTransactionId`.
**Fires from:** `RefundPaymentUseCase` once the vendor confirms the refund leg, inside the same DB transaction that records the refund row.
**Current consumers:** `ledger` (reverses the original posting set against the original `sourceId`), `settlement` (claws back the merchant payable), `invoice` (informational only — current scope does not flip status on refund).
