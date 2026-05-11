# Phase 1 Wave A — `ledger` module prompts

> **Branch:** `phase-1/ledger` — run all three sub-prompts sequentially in the same git worktree on the same branch.
> **Scope:** double-entry ledger of record. Owns `LedgerAccount` (sharded hot accounts), `JournalEntry`, `Posting`. Implements cross-module `RecordJournalEntryUseCase` + `GetAccountBalanceUseCase`. Consumes `PaymentCompletedEvent`.
> **Read first (every sub-prompt):** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md) — cross-cutting decisions (per-module routes, merge-train OpenAPI, pre-approved deps).

Sub-prompts:
1. [2a — schema + record + balance](#prompt-2a--ledger-schema--record--balance)
2. [2b — event listener + concurrency](#prompt-2b--ledger-event-listener--concurrency)
3. [2c — controllers + integrity job + coverage](#prompt-2c--ledger-controllers--integrity-job--coverage)

---

## Prompt 2a — ledger schema + record + balance

```
You are starting Phase 1 Wave A on the `conflux-ledger` module. This is the FIRST of THREE sequential sub-prompts (2a → 2b → 2c) on branch `phase-1/ledger`.

Your sub-scope: Flyway schema (with shard seed data), entities + repositories, the two locked cross-module use cases (`RecordJournalEntryUseCase` and `GetAccountBalanceUseCase`) implemented with idempotency + sharding + property tests for zero-sum invariants. NO event listener yet (that's 2b), NO controllers (that's 2c).

READ FIRST
- ARCHITECTURE.md (full file)
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2, §10.1 (property tests for ledger), §10.2 (concurrency)
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md "Cross-cutting decisions" section
- conflux-ledger/CLAUDE.md
- DOCS/features/ledger/PRD.md (full)
- DOCS/features/ledger/TECH_SPEC.md (full — invariant-heavy)
- conflux-ledger/src/main/java/pay/conflux/backend/ledger/usecase/ (locked: `RecordJournalEntryUseCase`, `GetAccountBalanceUseCase`, `JournalEntryRequest`, `PostingRequest`)
- conflux-common/src/main/java/pay/conflux/backend/common/money/Money.java + MoneyConverter.java

WORK ONLY IN
- conflux-ledger/src/main/java/pay/conflux/backend/ledger/{entity,repository,usecase.impl,constant}/...
- conflux-ledger/src/test/...
- conflux-application/src/main/resources/db/migration/V1002__ledger_schema.sql
- conflux-application/src/main/resources/db/migration/V1003__ledger_seed_system_accounts.sql
- conflux-application/src/test/java/pay/conflux/backend/ledger/...

DO NOT TOUCH
- conflux-ledger/src/main/java/pay/conflux/backend/ledger/usecase/ (interfaces/DTOs locked from Phase 0 Prompt 9)
- conflux-payment-core/events/ (locked event records; you only read them in 2b)
- conflux-common/, any other module
- common.constant.Routes (use LedgerRoutes in 2c)
- DOCS/contracts/openapi.json

DELIVERABLES

1. Flyway `V1002__ledger_schema.sql`:
   - `ledger_accounts`: id UUID PK, owner_id UUID NULL, type VARCHAR(16), code VARCHAR(64), shard_id INTEGER NOT NULL DEFAULT 0, currency VARCHAR(3) NOT NULL DEFAULT 'BDT', balance NUMERIC(19,4) NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0, audit columns. Unique `(owner_id, code, shard_id, currency)` using Postgres 15+ NULLS NOT DISTINCT (or workaround with COALESCE-on-expression index for older versions; document the choice).
   - `journal_entries`: id UUID PK, source_type VARCHAR(32), source_id VARCHAR(128), description TEXT, occurred_at TIMESTAMPTZ NOT NULL, audit. Unique `(source_type, source_id)` — the idempotency key.
   - `postings`: id UUID PK, journal_id UUID FK, account_id UUID FK, amount NUMERIC(19,4) NOT NULL, type VARCHAR(8) (DEBIT/CREDIT), currency VARCHAR(3), audit. Indexes `(account_id, created_at)`, `(journal_id)`.

2. Flyway `V1003__ledger_seed_system_accounts.sql`:
   - INSERT 10 shards each (shard_id 0..9) for: `ESCROW` (CLEARING type), `PLATFORM_REVENUE` (REVENUE), `VENDOR_PAYABLE` (LIABILITY). 30 rows total. owner_id = NULL for all (system accounts).
   - `MERCHANT_PAYABLE` is per-merchant, lazily provisioned on first posting — NO seed row.
   - Use deterministic UUIDs (`uuid_generate_v5`) keyed off the code+shard so re-running migrations is stable; OR use raw UUIDs and document them.

3. Entities (`pay.conflux.backend.ledger.entity`):
   - `LedgerAccount extends Auditable` — `@Version` on `version`. `@Enumerated(STRING)` everywhere. Use `MoneyConverter` from common for `balance` (composite of NUMERIC(19,4) + currency VARCHAR(3)). Domain method `applyPosting(Money amount, PostingType type)` mutates `balance` and lets JPA auto-increment `version`.
   - `JournalEntry extends Auditable` — append-only. Public constructor takes the request payload; no setters except for what JPA needs (private or package-private).
   - `Posting extends Auditable` — append-only.
   - Enums: `LedgerAccountType` (ASSET, LIABILITY, REVENUE, EXPENSE, CLEARING), `PostingType` (DEBIT, CREDIT), `JournalSourceType` (PAYMENT, REFUND, SETTLEMENT, FEE, ADJUSTMENT).

4. **Document the sign convention** in a Javadoc on `Posting`:
   - DEBIT amount stored positive; CREDIT amount stored positive; the `type` field carries the sign meaning.
   - For balance update: `applyPosting` increases the asset/expense account on DEBIT and decreases on CREDIT; the inverse for liability/revenue/equity. Encode this in `LedgerAccountType.applyDelta(currentBalance, postingType, amount)` so the entity has no business-logic branches.

5. Repositories (`pay.conflux.backend.ledger.repository`):
   - `LedgerAccountRepository`:
     - `Optional<LedgerAccount> findByOwnerIdAndCodeAndShardIdAndCurrency(UUID, String, int, String)`
     - `List<LedgerAccount> findByCodeAndCurrency(String, String)` — for system-account shard SUM aggregation
     - NO pessimistic-lock methods. Optimistic-lock-only by design.
   - `JournalEntryRepository`:
     - `boolean existsBySourceTypeAndSourceId(String, String)`
     - Paginated listings (used in 2c)
   - `PostingRepository`:
     - `Page<Posting> findByAccountId(UUID, Pageable)` (used in 2c)
     - `BigDecimal sumAmountByAccountId(UUID)` for the integrity verifier (used in 2c, but interface ready now)

6. Use-case implementations:
   - `RecordJournalEntryUseCaseImpl implements RecordJournalEntryUseCase`:
     1. **Idempotency:** if `journalEntryRepository.existsBySourceTypeAndSourceId(...)` → return silently (no-op).
     2. **Validation:** `SUM(postings.amount * sign(type)) == 0`. Use `Money.add` to avoid float drift. On non-zero → throw `InvalidOperationStateException` with message including the source key and the residual amount. Map the exception to `ErrorCode.INVALID_OPERATION_STATE` (do NOT add a new ErrorCode).
     3. **Persist** the `JournalEntry` first.
     4. **For each `PostingRequest`:** select the right shard. For system-account codes (ESCROW, PLATFORM_REVENUE, VENDOR_PAYABLE), `shardId = Math.floorMod(request.sourceId().hashCode(), 10)`. For per-merchant codes (MERCHANT_PAYABLE), `shardId = 0` and the account is provisioned-on-write if absent (atomic: try insert, on unique-violation re-load).
     5. **Apply posting via `applyPosting`** (which uses the entity's `@Version` for optimistic locking). Save the account; save the posting.
     6. NO retry inside this use case. Retry-on-`ObjectOptimisticLockingFailureException` is wired in 2b via Spring Retry.
     7. Annotate the impl `@Transactional`.
   - `GetAccountBalanceUseCaseImpl implements GetAccountBalanceUseCase`:
     - For codes the spec marks as system-level (ESCROW, PLATFORM_REVENUE, VENDOR_PAYABLE — keep this list as a static constant inside the impl; ownerId == null on the lookup): call `findByCodeAndCurrency` and return `SUM(balance)` as a `Money`. Document the read-after-write caveat (eventually-consistent during rapid updates).
     - For per-merchant codes (everything else, ownerId is the merchant ID): single-row read.

7. Per-module routes constants (`pay.conflux.backend.ledger.constant.LedgerRoutes`) — empty placeholder for now; controllers come in 2c.

TESTS (target: 65% module coverage on this sub-prompt; 80% by 2c)

Unit:
- Sign-convention round-trip on `LedgerAccountType.applyDelta`: a debit followed by a credit of equal amount nets to zero.
- Idempotency: calling `RecordJournalEntryUseCaseImpl` twice with the same `(sourceType, sourceId)` results in one `JournalEntry` row; second call returns silently.
- Validation: a request whose postings don't sum to zero throws `InvalidOperationStateException` and persists nothing.
- Shard selector: chi-square test on 10,000 random transaction IDs distributed across shards 0..9 (jqwik); chi-square statistic < 21.67 (99% confidence for 9 dof).

Integration (Testcontainers Postgres):
- Seed verification: after migration runs, `ledger_accounts` contains exactly 30 system rows (10×3 codes).
- End-to-end record: build a 3-posting journal (debit ESCROW, credit MERCHANT_PAYABLE — provisioning the merchant account on the fly, credit PLATFORM_REVENUE), commit; verify balances on each.
- Sharded balance: post 100 events with random transactionIds, debiting ESCROW each time. `GetAccountBalanceUseCase("ESCROW")` returns the SUM across all shards; postings landed across multiple shards (verify ≥ 5 distinct shardIds touched).

Property (jqwik, ≥ 200 tries):
- **Zero-sum journal:** for any randomized list of postings constrained to sum to zero, persistence succeeds and `SUM(postings.amount per journal_id) == 0` post-write.
- **Currency mismatch:** any posting with currency ≠ account currency is rejected by `Money` arithmetic — never coerced.
- **Balance ≡ postings (single-account, no concurrency):** for any random sequence of N postings against a single per-merchant account, after each write `account.balance == LedgerAccountType.applyDelta(0, ...)` cumulative.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-ledger -am verify` BUILD SUCCESS.
- JaCoCo ≥ 65%.
- ArchUnit + ModularityTests green.
- gitleaks, Spotless clean.
- Commit: `feat(ledger): schema + record + balance (2a)`.

FORBIDDEN
- Switching to pessimistic locking. Optimistic + retry is the contract.
- Adding any retry logic in this sub-prompt — that's 2b.
- Implementing the event listener (2b) or controllers (2c).
- Mutating `JournalEntry` or `Posting` after first save.
- Reading another module's entities/repositories.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample 3-posting journal SQL trace, jqwik tries summary, JaCoCo tail, shard distribution histogram from the chi-square test.
```

---

## Prompt 2b — ledger event listener + concurrency

```
You are continuing the `conflux-ledger` module on branch `phase-1/ledger`. Prompt 2a is committed. Your sub-scope: the `PaymentCompletedEvent` listener with `@TransactionalEventListener(AFTER_COMMIT)`, optimistic-lock retry via Spring Retry's `@Retryable` (added to root pom this sub-prompt), the Modulith replay-after-restart integration test, and the 100-thread concurrency test.

This sub-prompt is authorized to add Spring Retry to root pom per cross-cutting decision #3.

READ FIRST
- DOCS/features/ledger/TECH_SPEC.md §3.3 (event consumers), §4.2 (concurrency)
- conflux-payment-core/src/main/java/pay/conflux/backend/paymentcore/events/PaymentCompletedEvent.java (locked record — read constructor)
- DEVELOPMENT_WORKFLOW.md §10.2 (concurrency tests), §5.1 ("Modulith event replay")
- Spring Retry docs (use Context7 MCP if available): focus on `@EnableRetry`, `@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))`.
- The 2a commits

WORK ONLY IN — same as 2a, plus:
- Root `pom.xml` `<dependencyManagement>` (TWO entries: spring-retry + spring-aspects)
- `conflux-ledger/pom.xml` (TWO dep imports)

DO NOT TOUCH
- Any other root-pom section.
- 2a code unless extending logic (note the change in commit message).

DELIVERABLES

1. Root pom: add `org.springframework.retry:spring-retry:2.0.x` and `org.springframework:spring-aspects:6.x` to `<dependencyManagement>` (use the versions Spring Boot 3.x BOM aligns to — let the parent BOM resolve them where possible).

2. `conflux-ledger/pom.xml`: import both deps.

3. `@EnableRetry` on `conflux-ledger/src/main/java/pay/conflux/backend/ledger/config/LedgerConfig.java` (`@Configuration` class, also defines the `ledgerEventExecutor` `TaskExecutor` bean — `corePoolSize=4`, `maxPoolSize=16`, queue capacity 100, thread name prefix `ledger-evt-`).

4. **Refactor `RecordJournalEntryUseCaseImpl`** to add `@Retryable`:
   - Annotate the `execute` method with `@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2))`.
   - **Important:** `@Retryable` requires the call to come from outside the class for the proxy to fire. The internal idempotency check + transaction commit happens once per attempt. The retry restarts a fresh transaction each time — exactly what we want.
   - Add `@Recover` method that maps the third failure to `InvalidOperationStateException("Concurrent ledger update — please retry")` so callers see a defined error rather than the underlying Hibernate exception.

5. Event listener (`pay.conflux.backend.ledger.listener.PaymentCompletedEventListener`):
   - `@Component` + `@RequiredArgsConstructor`.
   - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` on the handler method.
   - `@Async("ledgerEventExecutor")` — non-blocking from the publisher's perspective.
   - On receiving `PaymentCompletedEvent`:
     1. Restore MDC `traceId` from the event field.
     2. Build a `JournalEntryRequest`:
        - `sourceType = "PAYMENT"`, `sourceId = event.transactionId().toString()`, `description = "Payment captured: " + merchantOrderReference`, `occurredAt = event.occurredAt()`.
        - 3 postings: debit ESCROW (sharded), credit MERCHANT_PAYABLE (per-merchant, ownerId=merchantId), credit PLATFORM_REVENUE (sharded) for `event.platformFee()`.
        - The merchant-payable amount = `event.amount() - event.platformFee()`.
     3. Call `recordJournalEntryUseCase.execute(request)`. Idempotency in 2a's impl makes Modulith replay safe.
     4. Clear MDC in `finally`.

6. **Stub-only** placeholder for `PaymentRefundedEventListener` — comment-out the file or write a `// TODO Wave C` class stub. DO NOT implement refund posting logic.

TESTS (target: cumulative module coverage 75% after 2b)

Unit:
- `PaymentCompletedEventListener` builds the right `JournalEntryRequest` from a synthetic event (mock the use case, assert the captured request).
- `@Retryable` retry behavior: with a mock that throws `ObjectOptimisticLockingFailureException` twice then succeeds, the use case is called 3 times and the third succeeds. Use `RetryTemplate`-style spy.

Integration (`@SpringBootTest` + Testcontainers Postgres):
- **End-to-end event flow:** publish a synthetic `PaymentCompletedEvent` via `ApplicationEventPublisher`; wait for the async listener (use `Awaitility`); assert exactly one `JournalEntry`, three `Postings`, balances correct on all three accounts. Re-publish the same event; assert no second journal (idempotency).
- **Modulith replay:** this is the trickiest test in the wave. Approach:
  1. Use a `@TestConfiguration` to replace the listener bean with a wrapper that throws on the first invocation per `transactionId`, then delegates on subsequent invocations (use a `ConcurrentHashMap<UUID, AtomicInteger>` for tracking).
  2. Publish event → first invocation throws → Modulith persists the publication as incomplete (the `event_publication.completion_date` stays null).
  3. Restart the Spring context (use `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` or programmatic restart via `SpringApplication.run`).
  4. After restart, call `IncompleteEventPublications.resubmitIncompletePublications()` (autowired bean).
  5. Assert: listener fires successfully (second invocation → throws is bypassed), `JournalEntry` exists, idempotency held — no double posting on subsequent re-publishes.
  - If full context restart is too heavy for CI, an acceptable simulation is: throw on first invocation → the publication remains incomplete in the table → manually invoke `resubmit` → the listener runs again → assert success. Document which approach you chose.
- **Concurrency:** `CountDownLatch` for 100 simultaneous `recordJournalEntryUseCase.execute(...)` against the SAME merchant account (different `sourceId` for each so idempotency doesn't suppress them). Final balance equals the sum of the 100 amounts; no postings lost; retries (if any) completed successfully.

Property (jqwik ≥ 100): leave 2a's properties intact; add no new properties here unless a new invariant emerges.

ACCEPTANCE CRITERIA (this sub-prompt)
- All from 2a still hold.
- Cumulative JaCoCo ≥ 75%.
- Modulith replay test green (or its documented simulation variant).
- 100-thread concurrency test green; final balance correct.
- Commit: `feat(ledger): event listener + retry + concurrency (2b)`.

FORBIDDEN
- Switching to pessimistic locking.
- Skipping the Modulith replay test on grounds of difficulty. If the full-restart variant is too heavy, the documented `resubmitIncompletePublications()`-direct-invocation variant is acceptable; not having either is a hard fail.
- Implementing refund posting (Wave C).
- Editing the locked `PaymentCompletedEvent` record.
- Adding any root-pom dep beyond the two listed.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample event-publication-table SQL trace before/after replay, sample concurrency-test thread log, cumulative JaCoCo tail.
```

---

## Prompt 2c — ledger controllers + integrity job + coverage

```
You are completing the `conflux-ledger` module on branch `phase-1/ledger`. Prompts 2a + 2b are committed. Your sub-scope: REST controllers (merchant + admin), the daily integrity job, the `ListJournalEntriesUseCase` and `VerifyTrialBalanceUseCase` (internal — not cross-module), final coverage push to ≥ 80%.

READ FIRST
- ARCHITECTURE.md §5 (controllers), §17 (security)
- DOCS/features/ledger/PRD.md §3 (user stories drive the endpoints)
- The 2a + 2b commits
- Current JaCoCo report

WORK ONLY IN — same as 2a/2b.

DELIVERABLES

1. Internal use cases (interface + impl, NOT in `usecase/` named-interface — these stay module-private; place in `usecase.internal/`):
   - `ListJournalEntriesUseCase` — paginated history with filters (date range, sourceType, optional ownerId for admin-only).
   - `VerifyTrialBalanceUseCase` — runs the global integrity check; returns `TrialBalanceReportDto(globalSumZero: boolean, balanceMismatches: List<AccountIntegrityRecord>, generatedAt: Instant)`.

2. REST controllers (`pay.conflux.backend.ledger.controller`):
   - `MerchantLedgerController` interface + impl:
     - `GET /api/v1/merchant/balance?code=MERCHANT_PAYABLE&currency=BDT` (defaults: code=MERCHANT_PAYABLE, currency=BDT).
     - `GET /api/v1/merchant/journal` paginated.
     - `@PreAuthorize("hasAuthority('MERCHANT')")`. Scope filter from `SecurityUtils.currentMerchantId()`. NEVER accept a merchantId query parameter.
   - `AdminLedgerController`:
     - `GET /api/v1/admin/ledger/journal` (paginated, filter by ownerId/sourceType/dateRange).
     - `GET /api/v1/admin/ledger/balance/{code}?currency=BDT` (system-account aggregated balance).
     - `GET /api/v1/admin/ledger/trial-balance` runs `VerifyTrialBalanceUseCase`.
     - `@PreAuthorize("hasAuthority('ADMIN_VIEWER')")` on read endpoints; `ADMIN_MANAGER` on trial-balance (operational action).
   - `LedgerRoutes` constants — fill in.

3. DTOs: `BalanceDto`, `JournalEntryDto`, `PostingDto`, `TrialBalanceReportDto`, `AccountIntegrityRecord`. NEVER include `version` in any outbound DTO. `JournalEntryDto` includes a list of `PostingDto`.

4. Mappers (MapStruct, `componentModel = "spring"`).

5. Specs (`pay.conflux.backend.ledger.spec.JournalEntrySpec`).

6. Background job (`pay.conflux.backend.ledger.job.LedgerIntegrityJob`):
   - `@Component`, `@Scheduled(cron = "0 0 3 * * *")` (3am daily UTC).
   - Calls `VerifyTrialBalanceUseCase.execute()`. On any inequality:
     - Log WARN with structured fields: account id, expected balance (SUM postings), actual balance, delta.
     - Increment Micrometer counter `conflux.ledger.integrity.failures`.
   - On clean pass: log INFO with the global SUM=0 confirmation.

7. Coverage push: open JaCoCo, identify gaps, add tests until ≥ 80% line / ≥ 70% branch on `conflux-ledger`.

TESTS

Unit:
- `VerifyTrialBalanceUseCase` returns clean report when ledger is balanced; reports mismatches when balances don't match SUM(postings).
- Mapper tests verify `version` is NOT in any DTO.

Integration:
- Run the Wave A test corpus (all integration tests from 2a + 2b), then invoke `LedgerIntegrityJob.runOnce()` directly via the bean. Assert the report is clean (global SUM == 0, no per-account mismatches).
- `MerchantLedgerController.getBalance` returns the caller's MERCHANT_PAYABLE balance; cannot read another merchant's balance even with a forged path/query.
- `AdminLedgerController.trialBalance` returns 200 + clean report; with a deliberately-injected unbalanced posting via direct repository write, the next call returns the mismatch row (then back-out the injection).

Property: keep 2a's. Optionally add: for any sequence of M committed journals, the trial-balance report's `globalSumZero` is always true.

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All from 2a + 2b still hold.
- JaCoCo ≥ 80% line, ≥ 70% branch on `conflux-ledger`.
- `mvn -pl conflux-ledger -am verify` BUILD SUCCESS.
- ArchUnit + Modulith green.
- PMD clean.
- gitleaks clean.
- Commit: `feat(ledger): controllers + integrity job + coverage (2c) — closes Wave A ledger`.

FORBIDDEN
- Exposing `ListJournalEntriesUseCase` or `VerifyTrialBalanceUseCase` as cross-module use-case interfaces. They are internal to ledger.
- Editing the locked use-case interfaces in `usecase/`.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample trial-balance report JSON, coverage tail per class.
```
