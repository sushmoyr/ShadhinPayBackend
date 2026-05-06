# PRD - Settlement & Reconciliation (shadhinpay-settlement)

## 1. Purpose
The Settlement & Reconciliation module is the final phase of the payment lifecycle. It ensures that funds collected from MFS vendors (bKash, Nagad, etc.) match ShadhinPay's internal ledger and are accurately distributed to merchants after deducting applicable fees and taxes.

## 2. Target Audience
*   **Merchants:** To receive payouts and view settlement statements.
*   **Platform Managers (Admins):** To perform daily reconciliation and authorize payouts.
*   **Finance/Accounting Team:** To ensure the platform remains balanced and compliant with tax regulations.

## 3. User Stories

### 3.1 Platform Manager Stories
*   **As an Admin**, I want to upload a daily settlement CSV from bKash so that the system can automatically match it against our transactions.
*   **As an Admin**, I want to see a list of "Exceptions" (transactions that exist in our ledger but not in the MFS report, or vice-versa).
*   **As an Admin**, I want to approve a bulk payout file (e.g., BEFTN) for all merchants who reached their settlement threshold.

### 3.2 Merchant Stories
*   **As a Merchant**, I want my funds to be settled into my bank account automatically every T+2 days.
*   **As a Merchant**, I want to download a Settlement Statement showing the breakdown of Gross Amount, Platform Fees, and VAT/Tax withheld.

## 4. Functional Requirements

### 4.1 Automated Reconciliation
*   The system must accept CSV/Excel settlement reports from MFS vendors.
*   **Matching Logic:** Match by `vendorTransactionId` and `amount`.
*   **Status Management:** Transactions move from `PENDING_SETTLEMENT` to `RECONCILED` once matched.

### 4.2 Payout Scheduling (T+N)
*   The system must calculate "Net Payable" for each merchant account.
*   **Net Payable = Gross Reconciled - Platform Fees - VAT/Tax - Previous Refunds.**
*   Support for configurable settlement cycles (default: T+2).

### 4.3 Tax & VAT Withholding
*   Automatically calculate and withhold Source Tax (AIT) and VAT on the **Platform Fee** portion as per Bangladesh Bank/NBR regulations.
*   Maintain a sub-ledger for "Withheld Taxes" for monthly reporting.

### 4.4 Payout Execution (BEFTN/NPSB)
*   Generate a standard bank-compliant payout file (CSV/Excel) for bulk bank transfers.
*   Record payout status: `SCHEDULED` -> `DISPATCHED` -> `SETTLED`.

## 5. Non-Functional Requirements
*   **Accuracy:** Zero-tolerance for rounding errors. Use `BigDecimal(19, 4)`.
*   **Auditability:** Every settlement action must be linked to a `JournalEntry` in the `shadhinpay-ledger`.
*   **Security:** Payout files must be accessible only to authorized Finance Admins and require 2FA for generation.

## 6. Acceptance Criteria
*   Reconciliation job identifies 100% of mismatches between the MFS report and the Internal Ledger.
*   Net Payable calculations match the Merchant's Settlement Statement exactly.
*   Payouts are only generated for `VERIFIED` and `ACTIVE` merchants.
