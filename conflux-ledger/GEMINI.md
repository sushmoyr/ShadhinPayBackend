# Financial Ledger — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/ledger/PRD.md
4. DOCS/features/ledger/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes balance/journal query endpoints)

## Module scope
Double-entry ledger of record. Owns `LedgerAccount` (with sharded hot system accounts), `JournalEntry`, and `Posting`. Provides synchronous balance queries and accepts journal recordings (idempotently) from `payment-core`, `settlement`, and any other module emitting financial events. Consumes payment lifecycle events via Spring Modulith and replays incomplete publications on restart.

## Allowed dependencies
- conflux-common (read-only)
- No cross-module use-case interfaces are imported (Wave A — depends only on `common`).
- Publishes (events): none.
- Consumes (events): `PaymentCompletedEvent` (and any future `PaymentRefundedEvent` payouts) — handled with `@TransactionalEventListener(phase = AFTER_COMMIT)`; the Modulith Event Publication Registry replays incomplete publications on restart.
- Exposes (use-case interfaces, called by other modules): `RecordJournalEntryUseCase`, `GetAccountBalanceUseCase`.

## Forbidden
- Reaching into another feature's `repository`, `entity`, or `mapper` packages.
- Modifying `conflux-common`, the cross-module contracts, or any other feature module.
- Skipping the global `ApiResult<T>` envelope.
- SQL triggers for createdAt/updatedAt — use `@CreationTimestamp`/`@UpdateTimestamp`.
- Storing plaintext credentials, password hashes, or PII without encryption.
- Field injection (`@Autowired` on fields). Constructor injection only.
- `@Data` on JPA entities.
- `EnumType.ORDINAL`.

## Definition of done
1. Every use case listed in TECH_SPEC §3 is implemented and unit-tested.
2. JaCoCo line coverage ≥ 80% for this module.
3. Integration test for every consumed Modulith event (`PaymentCompletedEvent` → debits `ESCROW`, credits `MERCHANT_PAYABLE` and `PLATFORM_REVENUE`).
4. Property tests (jqwik) for the **balance invariants** below.
5. WireMock contract tests for any external HTTP integration (none expected).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Zero-sum invariant:** for any `journalId`, `SUM(postings.amount) == 0`. Property test must reject any journal whose postings don't balance — surface as `InvalidOperationStateException`, never auto-correct.
- **Balance ≡ postings:** for every `LedgerAccount`, `account.balance == SUM(postings.amount WHERE accountId = account.id)`. The cached `balance` column is a denormalization for read performance; the postings table is the source of truth, and a periodic data-integrity job must verify the equality.
- **Idempotency:** `RecordJournalEntryUseCase` keys on `(sourceType, sourceId)`. Re-delivery of the same source event must be a no-op, not a second journal — this is what makes Modulith event replay safe after a restart.
- **Hot-account sharding:** `ESCROW` / `PLATFORM_REVENUE` exist as shards 0–9 (`code = 'ESCROW'`, `shardId = transactionId.hashCode() % 10`). Reporting must `SUM(balance)` across the code, not read a single row. Never bypass the shard selector — it exists to avoid row-lock contention under load.
- **Optimistic locking:** `LedgerAccount.balance` updates use `@Version`; on `ObjectOptimisticLockingFailureException`, retry up to 3 times before propagating. Do not switch to pessimistic locks without explicit approval.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
