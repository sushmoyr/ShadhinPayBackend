# PRD - Payment Core (conflux-payment-core)

## 1. Purpose
The Payment Core is the central processing engine of ConfluxPay. it orchestrates the lifecycle of a payment transaction from initial request to final settlement, abstracting the complexity of multiple MFS/Card vendors into a single, unified API.

## 2. Target Audience
*   **Merchants:** Who integrate the API to accept payments.
*   **Customers:** Who pay via the ConfluxPay checkout UI or API.
*   **System Modules:** `ledger`, `risk`, `quota`, and `notifications` depend on the state changes in this module.

## 3. User Stories

### 3.1 Merchant Stories
*   **As a Merchant**, I want to initiate a payment with a simple API call and receive a redirect URL for my customer.
*   **As a Merchant**, I want to ensure that if my system retries an API call, it won't charge the customer twice (Idempotency).
*   **As a Merchant**, I want to provide a `webhook_url` to receive real-time updates when a payment succeeds or fails.

### 3.2 Customer Stories
*   **As a Customer**, I want a smooth checkout experience where I can choose my preferred MFS (bKash, Nagad, etc.).

## 4. Functional Requirements

### 4.1 Payment Lifecycle (Immediate Debit)
Since we are skipping the Authorize/Capture flow, the system follows a direct-debit lifecycle:
1.  **INITIATED:** The merchant has requested a payment. A `payment_id` is created.
2.  **PENDING:** The customer has been redirected to the vendor's gateway.
3.  **COMPLETED:** The vendor has confirmed a successful debit.
4.  **FAILED:** The transaction was rejected by the vendor or timed out.
5.  **CANCELLED:** The customer explicitly cancelled the flow.

### 4.2 Unified API Interface
The module must provide a single set of endpoints that handle all vendors:
*   `POST /v1/payments`: Create a payment.
*   `GET /v1/payments/{id}`: Check status.
*   `POST /v1/payments/{id}/refund`: Initiate a refund.

### 4.3 Dual-Mode Routing
For every transaction, the engine must:
1.  Identify the `Business` and its `VendorConfig`.
2.  **Logic:** 
    *   If `mode == CUSTOM`: Use the merchant's encrypted credentials (from `conflux-provisioning`).
    *   If `mode == PARTNER`: Use ConfluxPay's global credentials.

### 4.4 Idempotency System
*   Every `POST` request must accept an `X-Idempotency-Key`.
*   If a key is reused within **24 hours**, the system must return the original response without re-processing the transaction.

### 4.5 Risk & Pre-flight Checks
Before calling a vendor adapter, the engine must:
1.  Verify the Merchant is `ACTIVE`.
2.  Call `conflux-risk` for fraud scoring/blocking.
3.  Call `conflux-quota` to check/increment the free-tier counter (for Partner Mode).

## 5. Non-Functional Requirements
*   **Consistency:** Use **Spring Modulith Events** to notify the `ledger` module. A transaction is not "Complete" until the ledger event is persisted.
*   **Reliability:** Webhooks must be sent with **Exponential Backoff** retries if the merchant's server is down.
*   **Availability:** The payment path must be highly available; vendor-specific failures must not crash the entire core.

## 6. Acceptance Criteria
*   Double-spending is prevented via idempotency keys.
*   Webhooks are cryptographically signed with HMAC-SHA256.
*   Transactions correctly route to the appropriate MFS adapter based on the Business configuration.
*   All monetary movements result in a corresponding Ledger entry, delivered via the Spring Modulith Event Publication Registry (see `common` §5).
