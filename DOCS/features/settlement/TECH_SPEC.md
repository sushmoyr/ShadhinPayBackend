# Tech Spec - Settlement & Reconciliation (conflux-settlement)

## 1. Architecture
Follows Hexagonal Architecture. This module is the primary writer for `SETTLEMENT` and `PAYOUT` journal entries in the `conflux-ledger`.

## 2. Domain Model & Schema

### 2.1 Entities

#### `ReconciliationJob`
*   `id`: UUID
*   `vendor`: Enum (`BKASH`, `NAGAD`, etc.)
*   `reportDate`: LocalDate
*   `status`: Enum (`PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`)
*   `totalMatchedCount`: Integer
*   `totalMismatchCount`: Integer
*   `uploadedBy`: UUID (Admin User)

#### `SettlementBatch` (Payouts)
*   `id`: UUID
*   `merchantId`: UUID
*   `amount`: Decimal (19, 4)
*   `status`: Enum (`SCHEDULED`, `DISPATCHED`, `SETTLED`, `FAILED`)
*   `payoutReference`: String (BEFTN Transaction ID)
*   `periodStart`: Instant
*   `periodEnd`: Instant

#### `ReconciliationException` (The "Breaks")
*   `id`: UUID
*   `jobId`: UUID
*   `transactionId`: UUID (Nullable if missing in our DB)
*   `vendorTrxId`: String
*   `reason`: Enum (`AMOUNT_MISMATCH`, `MISSING_IN_LOCAL`, `MISSING_IN_VENDOR`)
*   `status`: Enum (`OPEN`, `RESOLVED`)

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `ProcessSettlementReportUseCase`: Parses CSV/Excel, runs matching logic, and updates `Transaction` records.
*   `CalculateMerchantPayableUseCase`: Sums reconciled amounts and deducts fees/taxes for a specific period.
*   `GeneratePayoutFileUseCase`: Generates a bulk BEFTN file for `SCHEDULED` settlement batches.

### 3.2 Outbound Ports (Adapters)
*   `ReportParser`: Interface for vendor-specific CSV parsers (e.g., `BkashCsvParser`).
*   `SettlementRepository`: JPA for tracking batches and exceptions.

## 4. Business Logic Rules

### 4.1 The Reconciliation Matcher
1.  Iterate through every row in the Vendor Report.
2.  Lookup `Transaction` by `vendorTransactionId`.
3.  If found:
    *   Compare `amount`. If they differ -> Create `ReconciliationException (AMOUNT_MISMATCH)`.
    *   If same -> Update `Transaction.status = RECONCILED`.
4.  If not found -> Create `ReconciliationException (MISSING_IN_LOCAL)`.
5.  After the loop, any local `PENDING_SETTLEMENT` transactions for that day not touched -> Create `ReconciliationException (MISSING_IN_VENDOR)`.

### 4.2 Fee & Tax Calculation
For each reconciled transaction:
1.  `PlatformFee` = `Amount * FeePercentage` (e.g., 2.5% or fixed).
2.  `VAT` = `PlatformFee * 15%`.
3.  `NetToMerchant` = `Amount - PlatformFee - VAT`.
4.  **Journal Entry:** Trigger `conflux-ledger` to move funds from `ESCROW` to `MERCHANT_PAYABLE`.

## 5. Implementation Details

### 5.1 Batch Payout Process
*   A background job runs daily at 02:00 AM.
*   It aggregates all `NetToMerchant` amounts for transactions that reached their `settlement_date` (T+2).
*   Creates a `SettlementBatch` for each merchant.

### 5.2 BEFTN File Generation
*   Standard CSV format: `[BeneficiaryName, AccountNumber, RoutingNumber, Amount, Reference]`.
*   The `SettlementBatch` status moves to `DISPATCHED` once the file is generated.

## 6. Testing Strategy
*   **Reconciliation Test:** Use a mock bKash CSV with intentionally corrupted amounts and missing rows to verify that all 3 exception types are caught.
*   **Math Integrity Test:** Verify that `NetToMerchant + PlatformFee + VAT == GrossAmount` for every transaction.
*   **Concurrency Test:** Ensure that multiple admins uploading reports for different vendors simultaneously do not cause deadlocks on the `Transaction` table.
