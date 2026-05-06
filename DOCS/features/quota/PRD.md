# PRD - Quota & Metering (shadhinpay-quota)

## 1. Purpose
To manage and enforce usage-based limits for merchants, specifically the free-tier quota for transactions processed in **Partner Mode**. This module ensures accurate tracking of usage to trigger appropriate billing via the Ledger module.

## 2. Target Audience
*   **Merchants:** To understand their current usage against the free tier.
*   **Payment Core:** To verify if a transaction should be metered or billed before dispatching to an MFS vendor.
*   **System (Ledger):** To receive usage events for transaction fee calculations.

## 3. User Stories
*   **As a Merchant**, I want to see how many of my 10 free Partner-mode transactions I have used this month.
*   **As a Merchant**, I want my transactions to continue processing even after I exceed my 10 free transactions (billed at 0.39 BDT).
*   **As a System**, I want to track usage at the **Account level**, so a merchant with multiple businesses shares the same 10-transaction limit.
*   **As a System**, I want to reset all counters on the 1st of every month automatically.

## 4. Functional Requirements

### 4.1 Metering Logic
*   **Scope:** Only transactions in `PARTNER` mode are metered. `CUSTOM` mode transactions are recorded for analytics but do not consume the quota.
*   **Aggregation:** Usage is tracked per `User.id` (Merchant Account), not per Business.
*   **Threshold:** 10 free transactions per calendar month.
*   **Overage:** The 11th transaction and onwards are successful but are flagged as "Billable" for the Ledger module.

### 4.2 Counter Management
*   **Hot Counters:** Use **Redis** for sub-millisecond incrementing and checking during the payment flow.
*   **Persistence:** Sync counters to the database periodically or upon period reset for historical auditing.
*   **Reset Cycle:** Automated reset on the **1st of every month** at 00:00 UTC.

### 4.3 Payment Integration
The module must provide a fast "Check and Increment" API for the `payment-core`:
1.  Is this a Partner Mode transaction?
2.  Increment counter for the Account.
3.  Return `IS_FREE` (true/false).

## 5. Non-Functional Requirements
*   **Performance:** The quota check must not add more than 10ms to the payment initiation latency.
*   **Availability:** If Redis is down, the system must **Fail-Open** (allow the transaction and log the failure for later reconciliation).
*   **Consistency:** Eventual consistency between Redis counters and the DB is acceptable, but the "Check and Increment" must be atomic in Redis.

## 6. Acceptance Criteria
*   A merchant with two businesses uses 5 free transactions on each; the 11th transaction on either business is marked as billable.
*   Custom mode transactions do not increment the counter.
*   Counters reset correctly at the start of a new month.
*   The system handles high-concurrency increments without race conditions (using Redis `INCR`).
