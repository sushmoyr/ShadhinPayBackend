# Phase 1 Wave D — Bkash Execute step wiring (cross-cutting)

> **Branch:** `phase-1/wave-d-bkash-execute` — single small `payment-core` patch.
>
> **Scope:** close the cross-cutting follow-up from Wave C. `BkashAdapter.confirm(paymentID, creds)` shipped in Wave C as a public adapter method but is NOT yet invoked anywhere. Wave B 8c's `payment-core` callback handler assumes synchronous capture in `initiate(...)`, which is correct for SSLCommerz / Stripe / Mock but wrong for Bkash Tokenized Checkout v1.2 (which requires a separate Execute call after the user authorizes the payment in the Bkash app).
>
> **Read first:** [Wave D index](../PHASE_1_WAVE_D_PROMPTS.md); the Wave C report's "Follow-up: Bkash Execute step" entry; `DOCS/prompts/wave-c/bkash.md` (search for "Execute" and `confirm`); `conflux-payment-core/CLAUDE.md`; the payment-core callback handler from Wave B 8b/8c.

---

## Prompt — Bkash Execute wiring

```
You are running the Wave D cross-cutting Bkash Execute follow-up on branch
`phase-1/wave-d-bkash-execute`. Your scope is a SMALL `payment-core` patch that
invokes `BkashAdapter.confirm(...)` on the Bkash callback path before marking
the transaction COMPLETED. You implement NO adapter logic and add NO new use
case interfaces.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (cross-cutting decisions)
- DOCS/prompts/wave-c/bkash.md — search for `confirm` and "Execute" to understand the contract
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/bkash/BkashAdapter.java (Wave C deliverable — read the `confirm` method's signature and behavior)
- conflux-payment-core/CLAUDE.md
- conflux-payment-core/src/main/java/pay/conflux/backend/paymentcore/usecase/impl/{the callback-handling use case shipped in Wave B 8b/8c} — grep for "callback" or "HandleVendorCallback" to find it
- DOCS/features/payment-core/TECH_SPEC.md (callback flow)

WORK ONLY IN
- conflux-payment-core/src/main/java/pay/conflux/backend/paymentcore/usecase/impl/{the callback-handling use case} — minimal edit
- conflux-payment-core/src/test/java/... (extend the existing callback IT to cover the Bkash branch)
- conflux-application/src/test/java/pay/conflux/backend/paymentcore/* (extend the end-to-end Bkash IT with WireMock if one exists from Wave C)

DO NOT TOUCH
- `BkashAdapter` or any other adapter.
- `PaymentProvider` port (do NOT add `confirm` to the port — it remains a Bkash-only public method).
- Any other use case beyond the callback handler.
- Any DTO, controller, or mapper outside the strict minimum the patch requires.
- Any Flyway migration.
- Root pom.xml.

DELIVERABLES

1. **Callback handler patch** — inside the use case that handles
   `POST /api/v1/payments/callback/{vendor}` (whatever Wave B 8b/8c named it),
   add a branch:
   ```java
   if (transaction.getVendor() == Vendor.BKASH) {
       // Bkash Tokenized Checkout v1.2: callback indicates user authorized;
       // server-side Execute call captures the payment.
       VendorResponse confirmResponse = bkashAdapter.confirm(
           transaction.getVendorTransactionId(),
           vendorCredentialsFor(transaction)
       );
       if (confirmResponse.status() != VendorStatus.SUCCESS) {
           // Map and rethrow per the existing error-handling pattern.
           throw new MfsAdapterException(confirmResponse.errorCode(),
               confirmResponse.errorMessage());
       }
   }
   // existing code: mark COMPLETED, publish PaymentCompletedEvent, etc.
   ```
   The branch lives INSIDE the existing `@Transactional` boundary so a failed
   Execute rolls back any prior state changes from the callback handler.

2. **`BkashAdapter` injection** — the callback use case adds a private final
   `BkashAdapter bkashAdapter` field. Resolving it requires that the use case
   know about a concrete adapter, which the `PaymentProvider` abstraction was
   designed to avoid. This is an intentional exception:
   - The `PaymentProvider` port intentionally does NOT have `confirm` (per
     Wave C decision — confirm is Bkash-specific).
   - The callback handler is the only place that needs adapter-specific
     dispatch; the rest of the system continues to talk to `PaymentProvider`.
   - Mark this with a clear Javadoc comment on the field: `// Bkash Execute
     step is vendor-specific and intentionally not on the PaymentProvider port;
     see DOCS/prompts/wave-d/bkash-execute-wiring.md`.

3. **No new use case, controller, DTO, or route.** If you find yourself adding
   any of these, STOP — the patch has scope-crept and needs to be reconsidered.

TESTS

Integration (extend existing tests; do NOT add a new test class if an
equivalent one exists):
- The existing Bkash callback IT (Wave C WireMock contract test for callback)
  is extended with:
  - **Successful Execute:** WireMock stubs `/tokenized/checkout/execute` to
    return SUCCESS; assert transaction transitions to `COMPLETED` and
    `PaymentCompletedEvent` is published.
  - **Failed Execute:** WireMock stubs `/tokenized/checkout/execute` to return
    `2056` (DuplicateTransaction) or similar; assert transaction stays in
    `PENDING_RECOVERY` (or whatever state the existing handler uses for
    callback-then-confirm-failed), `PaymentCompletedEvent` is NOT published,
    and the error is logged.
- An assertion that for `Vendor.SSLCOMMERZ` (and any other non-Bkash vendor),
  the callback handler does NOT invoke `BkashAdapter.confirm` — verify with
  Mockito `verifyNoInteractions(bkashAdapter)` on a SSLCommerz callback IT.

Unit:
- Optional: a direct unit test of the callback use case mocking
  `BkashAdapter` and the other adapters. If the existing test class is
  Mockito-based, extend it. If it's only integration-tested via WireMock,
  don't introduce a new unit test seam — the IT coverage is sufficient.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-payment-core,conflux-application -am verify` BUILD SUCCESS.
- The Wave C Bkash WireMock contract test is still green.
- No per-module JaCoCo gate drop.
- ArchUnit + Modulith green.
- Single commit: `feat(payment-core): wire Bkash Execute on redirect callback (wave-d follow-up)`.

FORBIDDEN
- Adding `confirm(...)` to the `PaymentProvider` port.
- Adding any new use case, controller, DTO, or route.
- Touching `BkashAdapter` or any other adapter implementation.
- Adding a root-pom dependency.
- Skipping the SSLCommerz no-invocation assertion (the negative test is what
  proves the branch is Bkash-only).

Output: the diff of the callback use case (should be ~10–15 lines of code +
a Javadoc comment), the diff of the test class showing the two new scenarios,
JaCoCo tail showing no regression.
```
