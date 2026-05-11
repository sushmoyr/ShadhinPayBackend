# Tech Spec - Financial Ledger (conflux-ledger)

## 1. System Architecture
This module follows the **Hexagonal Architecture** pattern defined in `ARCHITECTURE.md`. It acts as a downstream consumer of payment events and an upstream provider of balance information.

## 2. Domain Model & Schema

### 2.1 Entities

#### `LedgerAccount`
Represents a financial account in the chart of accounts.
*   `id`: UUID (Primary Key)
*   `ownerId`: UUID (Merchant Account ID or NULL for system accounts)
*   `type`: Enum (`ASSET`, `LIABILITY`, `REVENUE`, `EXPENSE`, `CLEARING`)
*   `code`: String (e.g., `MERCHANT_PAYABLE`, `PLATFORM_FEES`)
*   `shardId`: Integer (Default: 0) - **Used for sharding hot system accounts**
*   `currency`: String (Default: `BDT`)
*   `balance`: Decimal (19, 4) - **Cached balance for performance**
*   `version`: Long (Optimistic Locking)

...

### 4.4 Sharding for Hot System Accounts
- **The Problem:** Global accounts like `ESCROW` and `PLATFORM_REVENUE` are hit by every transaction, causing row-level lock contention.
- **The Fix:** Create 10 shards for each hot system account (e.g., `ESCROW_0` to `ESCROW_9`).
- **Logic:** When recording a journal entry, the system selects a shard using `transactionId.hashCode() % 10`.
- **Reporting:** To get the true balance of `ESCROW`, the system must `SUM(balance) WHERE code = 'ESCROW'`.

#### `JournalEntry`
Groups a set of related postings for a single business event.
*   `id`: UUID
*   `sourceType`: Enum (`PAYMENT`, `REFUND`, `SETTLEMENT`, `FEE`, `ADJUSTMENT`)
*   `sourceId`: String (ID of the transaction in the source module)
*   `description`: String
*   `occurredAt`: Instant

#### `Posting`
A single debit or credit entry.
*   `id`: UUID
*   `journalId`: UUID (Foreign Key)
*   `accountId`: UUID (Foreign Key)
*   `amount`: Decimal (19, 4) - **Positive for Debit, Negative for Credit** (Standard Accounting representation)
*   `type`: Enum (`DEBIT`, `CREDIT`)

### 2.2 Database Constraints
*   `postings`: Index on `accountId`, `journalId`.
*   `ledger_accounts`: Unique constraint on `(ownerId, code)`.

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `GetAccountBalanceUseCase`: Returns the current balance for a merchant's specific account code.
*   `RecordJournalEntryUseCase`: Atomically records a journal entry and its postings, and updates account balances.
*   `ListJournalEntriesUseCase`: Paginated history of ledger entries for a merchant or admin.

### 3.2 Outbound Ports (Adapters)
*   `LedgerAccountRepository`: JPA Repository for balance updates.
*   `JournalRepository`: JPA Repository for auditing.

### 3.3 Event Consumers
The ledger module consumes events published by `conflux-payment-core` via the **Spring Modulith Event Publication Registry** (see `common` §5). Listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)` so postings are only recorded after the source transaction has committed; incomplete publications are replayed on application restart.

*   `PaymentCapturedEvent` -> Triggers:
    1. Debit `ESCROW`
    2. Credit `MERCHANT_PAYABLE`
    3. Credit `PLATFORM_REVENUE` (Fee portion)

Idempotency is enforced via the `(sourceType, sourceId)` uniqueness check in `RecordJournalEntryUseCase` (§4.3), which makes redelivery safe.

## 4. Business Logic Rules

### 4.1 Balance Invariants
*   The sum of all `postings.amount` for a given `journalId` must be **zero**.
*   The `ledger_account.balance` must always equal the sum of its `postings.amount`.

### 4.2 Handling Concurrency
*   When updating `ledger_account.balance`, use **Optimistic Locking** (`@Version`) to prevent race conditions during high-frequency postings.
*   Retry logic (max 3 times) for `ObjectOptimisticLockingFailureException`.

### 4.3 Idempotency
*   `RecordJournalEntryUseCase` must be idempotent. If a `sourceType` and `sourceId` combination already exists in `JournalEntry`, the request should be ignored to prevent double-posting.

## 5. Implementation Details

### 5.1 Service Layer Logic
```java
@UseCase
@Transactional
public class RecordJournalEntryUseCaseImpl implements RecordJournalEntryUseCase {
    @Override
    public void execute(LedgerEntryRequest request) {
        // 1. Check idempotency
        if (journalRepo.existsBySourceTypeAndSourceId(request.type(), request.id())) return;

        // 2. Create Journal Entry
        JournalEntry journal = new JournalEntry(request);
        
        // 3. Process Postings & Update Balances
        for (PostingRequest p : request.postings()) {
            LedgerAccount acc = accountRepo.findByIdWithLock(p.accountId());
            acc.updateBalance(p.amount());
            postingRepo.save(new Posting(journal, acc, p.amount()));
        }
        
        journalRepo.save(journal);
    }
}
```

## 6. Testing Strategy
*   **Unit Tests:** Verify that balanced postings are required (sum to zero).
*   **Integration Tests:** Verify that `OptimisticLocking` handles concurrent balance updates correctly.
*   **Data Integrity Test:** A periodic background job to verify that `SUM(postings.amount) == account.balance` for all accounts.
