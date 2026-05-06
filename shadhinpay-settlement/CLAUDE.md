# Settlement & Reconciliation — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/settlement/PRD.md
4. DOCS/features/settlement/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes admin reconciliation-upload, batch-status, and BEFTN-export endpoints)

## Module scope
Vendor-report reconciliation, fee/VAT/AIT computation, and T+2 merchant payouts. Owns `ReconciliationJob`, `SettlementBatch`, and `ReconciliationException` (the "breaks"). Parses vendor CSVs through per-vendor `ReportParser` adapters, matches to local transactions, computes fees/taxes, and writes `SETTLEMENT`/`PAYOUT` journal entries through the ledger. Generates BEFTN payout files daily.

## Allowed dependencies
- shadhinpay-common (read-only)
- Cross-module use-case interfaces consumed:
  - `ledger.RecordJournalEntryUseCase`
  - `ledger.GetAccountBalanceUseCase`
  - A `payment-core` use-case interface to read/update `Transaction` reconciliation state (resolve the exact name with `payment-core` agent — see "Spec ambiguous" below; **do not** reach into `payment-core`'s repository/entity to do this).
- Publishes (events): none in Phase 1.
- Consumes (events): none in Phase 1.
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
3. Integration test for the daily T+2 payout job and the reconciliation upload flow (Testcontainers Postgres). Phase 2 §5.1 covers the canonical "1 amount-mismatch + 1 missing-in-local + 1 missing-in-vendor" scenario — the test must already exist here.
4. Property tests (jqwik) for the **fee/VAT math invariant** below.
5. WireMock contract tests for any external HTTP integration (BEFTN file dispatch is filesystem-based in Phase 1; no HTTP).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Zero tolerance for rounding error.** All money math uses `BigDecimal` at scale 4 with `RoundingMode.HALF_EVEN` (the `common` helpers). For every transaction: `Gross == NetToMerchant + PlatformFee + VAT` must hold exactly — property-test it across randomized fee schedules. A summed-batch off-by-one-paisa breaks BEFTN reconciliation upstream at the bank.
- **Every settlement action is linked to a `JournalEntry`.** Moving funds from `ESCROW` to `MERCHANT_PAYABLE`, deducting `PLATFORM_FEES`, recording `VAT` — each step calls `ledger.RecordJournalEntryUseCase` with a unique `(sourceType=SETTLEMENT, sourceId)` so replays are safe. Never mutate balances by any other path.
- **Reconciliation matcher is exhaustive:** every vendor row produces either a `RECONCILED` transition or one of three `ReconciliationException` reasons (`AMOUNT_MISMATCH`, `MISSING_IN_LOCAL`, `MISSING_IN_VENDOR`). After the matcher loop, untouched local `PENDING_SETTLEMENT` transactions for the report date must be flagged `MISSING_IN_VENDOR` — easy to forget; the canonical Phase 2 test will catch it.
- **Concurrent uploads from multiple admins** for different vendors must not deadlock on the `Transaction` table (TECH_SPEC §6). Lock granularity is per-vendor / per-day; do not take table-level locks.
- **BEFTN file is the source of truth for the bank.** Once a `SettlementBatch` moves to `DISPATCHED`, regeneration must not produce a different file for the same batch — the file format and ordering is part of the contract.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.

A specific known ambiguity for this module: the exact `payment-core` use-case interface that lets settlement update `Transaction.status = RECONCILED` is not enumerated in DEVELOPMENT_WORKFLOW.md §3.3. Coordinate with the `payment-core` agent — likely named `UpdateTransactionReconciliationStatusUseCase` or equivalent — and add it to the cross-module contracts list before implementing.
