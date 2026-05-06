# Tech Spec - Quota & Metering (shadhinpay-quota)

## 1. Architecture
Follows Hexagonal Architecture. This module acts as a high-performance utility for the `payment-core`.

## 2. Domain Model & Schema

### 2.1 Entities (Postgres)

#### `QuotaUsage`
Stores the persisted historical usage for auditing and billing.
*   `id`: UUID
*   `merchantId`: UUID (References `User.id`)
*   `period`: String (Format: `YYYY-MM`, e.g., "2026-05")
*   `partnerModeCount`: Integer
*   `updatedAt`: Instant

### 2.2 Redis Key Strategy
*   **Key:** `quota:{merchantId}:{period}`
*   **Value:** Integer (Atomic Counter)
*   **TTL:** 35 Days (To allow for end-of-month processing and reconciliation).

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   **`ReserveQuotaUseCase`**: 
    *   Input: `merchantId`.
    *   Logic: Increments a temporary "Pending" counter in Redis.
    *   Output: `QuotaStatus` (Enum: `FREE`, `BILLABLE`).
*   **`ConfirmQuotaUseCase`**:
    *   Input: `merchantId`.
    *   Logic: Moves the reservation from "Pending" to "Final" usage in the DB/Redis.
*   **`ReleaseQuotaUseCase`**:
    *   Input: `merchantId`.
    *   Logic: Decrements the "Pending" counter if the transaction failed.
*   **`GetUsageUseCase`**: Returns current usage from Redis for the merchant dashboard.
*   **`MonthlyResetJob`**: A scheduled task to persist Redis counters to the `QuotaUsage` table.

### 3.2 Outbound Ports (Adapters)
*   **`QuotaRepository`**: JPA adapter for Postgres persistence.
*   **`CachePort`**: Interface for Redis operations (Atomic `INCR`).

## 4. Business Logic Rules

### 4.1 Soft-Reservation Flow (Consistency)
To prevent "Lost Quota" when a payment fails after initiation:
1.  **Reserve:** `payment-core` calls `ReserveQuotaUseCase`. Redis `INCR` is performed on a `pending_quota:{id}` key.
2.  **Outcome:**
    *   If payment succeeds -> `ConfirmQuotaUseCase` is called. The `pending_quota` is decremented and `final_quota` is incremented.
    *   If payment fails/expires -> `ReleaseQuotaUseCase` is called. The `pending_quota` is decremented.
3.  **Cleanup:** A background job reconciles any `pending_quota` reservations older than 30 minutes to prevent leaked reservations.

### 4.2 Monthly Persistence & Reset
On the 1st of every month:
1.  Identify all keys for the previous month in Redis (or maintain a "Dirty Merchants" set).
2.  Iterate and save the final counts to `QuotaUsage` in Postgres.
3.  The `payment-core` naturally starts hitting the new month's key (`quota:{id}:2026-06`) automatically based on the current system time.

## 5. Implementation Details

### 5.1 Fail-Open Mechanism
The `CheckAndIncrementQuotaUseCase` must be wrapped in a `try-catch`. Any Redis connection error or timeout must default to returning `FREE`. A separate error log should be generated for manual reconciliation if needed.

### 5.2 Integration with Payment Core
`payment-core` calls `shadhinpay-quota` **synchronously** during the initiation phase.
*   If `mode == CUSTOM` -> Skip Quota check.
*   If `mode == PARTNER` -> Call `checkAndIncrement`.

## 6. Testing Strategy
*   **Concurrency Test:** Use `CountDownLatch` in an integration test to simulate 100 simultaneous requests for the same merchant and verify the counter reaches exactly 100.
*   **Rollover Test:** Verify that on May 31st 23:59:59 and June 1st 00:00:01, two different Redis keys are used.
*   **Resiliency Test:** Shutdown the Redis container and verify that `payment-core` still successfully processes payments (Fail-Open).
