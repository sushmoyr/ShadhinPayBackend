# PRD - Financial Ledger (conflux-ledger)

## 1. Purpose
The Financial Ledger is the "Source of Truth" for all monetary movement within ConfluxPay. It ensures financial integrity through double-entry bookkeeping, providing a clear audit trail for merchants, platform managers, and regulatory bodies.

## 2. Target Audience
*   **Merchants:** To view accurate balances and transaction history.
*   **Platform Managers:** To perform reconciliation, audit the system, and manage settlements.
*   **System Modules:** `payment-core`, `settlement`, and `fees` rely on the ledger to record activities.

## 3. User Stories
*   **As a Merchant**, I want to see my current available balance across all my businesses.
*   **As a Platform Manager**, I want to see a global journal of all entries to ensure the system is balanced.
*   **As a System**, I want to record a payment as a transfer from `ESCROW` to `MERCHANT_PAYABLE` after a successful capture.
*   **As a System**, I want to synchronously check if a merchant has enough funds before authorizing a payout.

## 4. Functional Requirements

### 4.1 Chart of Accounts (COA)
The system must support the following internal account types:
*   **Assets:** `CASH_AT_BANK` (Real money held in MFS/Bank accounts).
*   **Liabilities:** `MERCHANT_PAYABLE` (Funds owed to merchants), `VENDOR_PAYABLE` (Fees owed to MFS providers).
*   **Equity/Revenue:** `PLATFORM_REVENUE` (Transaction fees earned by ConfluxPay).
*   **Clearing:** `ESCROW` (Funds in transit/pending settlement).

### 4.2 Double-Entry Bookkeeping
*   Every financial event must generate a **Journal Entry**.
*   Each Journal Entry must contain at least two **Postings** (one Debit, one Credit).
*   The sum of all Postings in a Journal Entry must be zero.

### 4.3 Transaction Types
The ledger must support recording the following:
*   **Payment Capture:** Move from Escrow to Merchant (minus fees).
*   **Fee Accrual:** Record the variable (eg: 0.39 BDT) platform fee.
*   **Refunds:** Reverse funds from Merchant back to Escrow/Customer.
*   **Payouts:** Transfer from `MERCHANT_PAYABLE` to `CASH_AT_BANK` (external).

### 4.4 Consistency & Performance
*   **Sync Balance Check:** Payout-related modules must be able to query the "Available Balance" synchronously.
*   **Async Event Processing:** Most ledger entries will be created by listening to Domain Events from `payment-core`, delivered via the Spring Modulith Event Publication Registry (see `common` §5).

## 5. Non-Functional Requirements
*   **Immutability:** Entries are append-only. No `UPDATE` or `DELETE` on posting tables.
*   **Scalability:** High-frequency writes for postings. Use database indexing optimized for `account_id` and `created_at`.
*   **Multi-Currency:** Support BDT by default, but the schema must include a `currency` field.

## 6. Acceptance Criteria
*   Balance inquiry returns correct results immediately after a synchronous write.
*   The sum of all ledger entries in the system is always zero (Global Trial Balance).
*   Every posting is linked to a valid merchant account and a source transaction.
