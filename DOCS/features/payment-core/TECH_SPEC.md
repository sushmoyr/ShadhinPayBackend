# Tech Spec - Payment Core (conflux-payment-core)

## 1. Architecture
Follows Hexagonal Architecture. This module is the "Orchestrator" that coordinates between Merchants, MFS Adapters, and the Ledger.

## 2. Domain Model & Schema

### 2.1 Entities

#### `Transaction`
*   `id`: UUID
*   `merchantId`: UUID
*   `businessId`: UUID
*   `amount`: Decimal (19, 4)
*   `currency`: String (Default: `BDT`)
*   `status`: Enum (`INITIATED`, `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`, `PENDING_RECOVERY`)
*   `vendor`: Enum (`BKASH`, `NAGAD`, etc.)
*   `mode`: Enum (`PARTNER`, `CUSTOM`)
*   `merchantOrderReference`: String (Provided by merchant)
*   `vendorTransactionId`: String (Returned by MFS)
*   `metadata`: JSONB (Custom K/V pairs)
*   `callbackUrl`: String (Where customer is redirected)
*   `webhookUrl`: String (Server-to-server notification)
*   `retryCount`: Integer (For webhook delivery)

#### `WebhookOutbox` (The "Reliable Delivery" Queue)
*   `id`: UUID
*   `transactionId`: UUID
*   `payload`: JSONB
*   `status`: Enum (`PENDING`, `SENT`, `FAILED`)
*   `nextAttemptAt`: Instant

#### `IdempotencyRecord`
*   `requestKey`: String (Unique)
*   `merchantId`: UUID
*   `responsePayload`: JSONB
*   `expiresAt`: Instant

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `InitiatePaymentUseCase`: Validates request, checks quota/risk, creates `Transaction`, and returns vendor redirect URL.
*   `ProcessVendorCallbackUseCase`: Handles the return of the customer from MFS (Success/Cancel/Fail).
*   `HandleWebhookRetryUseCase`: Background job for retrying failed merchant notifications.

### 3.2 Outbound Ports (Adapters)
*   **MFS Port:** A common interface that all `conflux-adapters` must implement.
*   **Event Publisher:** Spring Modulith publisher for `PaymentCompletedEvent`.

## 4. Business Logic Rules

### 4.1 Orchestration Flow
1.  **Request Validation:** Check `X-Idempotency-Key` in Redis. If found, return cached response.
2.  **Pre-flight:** 
    *   Call `ProvisioningModule` to get `Business` and `VendorConfig`.
    *   Call `QuotaModule` to check/reserve quota (if Partner mode).
    *   Call `RiskModule` for scoring.
3.  **Persistence:** Save `Transaction` with status `INITIATED`.
4.  **Vendor Dispatch:** Call the appropriate adapter (bKash/Nagad) with the correct `credentials`.
5.  **State Transition:** Update status to `PENDING` and return `redirect_url` to merchant.

### 4.2 Completion & Recovery Logic
#### A. The "Zombie" Recovery
To handle MFS timeouts:
1.  If a vendor request times out, mark `Transaction.status = PENDING_RECOVERY`.
2.  A background job (`ReconciliationScheduler`) polls `queryStatus` for all `PENDING_RECOVERY` transactions.
3.  **Finality:** The status is only moved to `COMPLETED` or `FAILED` based on the vendor's definitive response.

#### B. Reliable Webhook Delivery
To protect the core from slow merchant servers:
1.  When a transaction completes, insert a record into `WebhookOutbox` in the same DB transaction.
2.  A separate `WebhookDispatcher` (isolated thread pool) reads `PENDING` records and attempts HTTP delivery.
3.  If a delivery fails, it schedules a `nextAttemptAt` using exponential backoff.

### 4.3 Webhook Security & Retries
*   **Signature:** Sign payload with `webhookSecret` (HMAC-SHA256).
*   **Retry Policy:** Exponential backoff (1m, 5m, 15m, 1h, 6h, 24h).

## 5. Implementation Details

### 5.1 Redis for Idempotency
- Key: `idempotency:{merchantId}:{requestKey}`
- Value: JSON of the initial `PaymentResponse`.
- TTL: 24 Hours.

### 5.2 Transactional Integrity
The MFS callback handler must be wrapped in a transaction that:
1. Updates the `Transaction` record.
2. Marks the `IdempotencyRecord` as finalized.
3. Persists the `PaymentCompletedEvent` (via Modulith).

## 6. Testing Strategy
*   **Idempotency Test:** Concurrent requests with the same key must result in exactly one transaction.
*   **Routing Test:** Verify that `CUSTOM` mode correctly passes merchant credentials to the adapter.
*   **Failure Recovery:** Simulate a Ledger module crash and verify that the `PaymentCompletedEvent` is redelivered by Spring Modulith once the module is back online.
