# Tech Spec - Invoice Management (shadhinpay-invoice)

## 1. Architecture
Follows Hexagonal Architecture. This module is a consumer of the `shadhinpay-payment-core` and a provider of public-facing web content.

## 2. Domain Model & Schema

### 2.1 Entities

#### `Invoice`
*   `id`: UUID
*   `merchantId`: UUID
*   `businessId`: UUID
*   `slug`: String (Unique, Indexed) - Cryptographically secure slug for the public URL.
*   `invoiceNumber`: String (Merchant-provided or auto-generated)
*   `status`: Enum (`INITIATED`, `VIEWED`, `PAID`, `EXPIRED`, `CANCELLED`, `FAILED`)
*   `customerName`: String
*   `customerEmail`: String
*   `customerPhone`: String
*   `totalAmount`: Decimal (19, 4)
*   `currency`: String (Default: `BDT`)
*   `expiresAt`: Instant (Nullable for permanent invoices)
*   `note`: String
*   `paymentId`: UUID (Nullable, linked to `Transaction.id` in `payment-core` once payment is initiated)

#### `InvoiceItem`
*   `id`: UUID
*   `invoiceId`: UUID (FK to Invoice)
*   `description`: String
*   `quantity`: Integer
*   `unitPrice`: Decimal (19, 4)

### 2.2 Schema Constraints
*   `Invoice`: Unique index on `slug`.
*   `Invoice`: Index on `businessId`, `status`.

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   **Merchant API:**
    *   `CreateInvoiceUseCase`: Generates `Invoice`, `InvoiceItem`s, and the secure `slug`.
    *   `GetInvoiceDetailsUseCase`: For merchant dashboard.
    *   `CancelInvoiceUseCase`: Manual voiding.
*   **Public API:**
    *   `GetPublicInvoiceUseCase`: Retrieves invoice data via `slug` for the payment page.
    *   `InitiateInvoicePaymentUseCase`: Calls `payment-core.InitiatePaymentUseCase` to generate a redirect URL for the customer.

### 3.2 Event Listeners (Spring Modulith)
*   `on(PaymentCompletedEvent event)`:
    *   Checks if the `PaymentCompletedEvent.metadata` contains an `invoice_id`.
    *   If yes, updates the `Invoice` status to `PAID`.

## 4. Business Logic Rules

### 4.1 Slug Generation
*   Slugs must be URL-safe Base64 or long UUIDs to prevent brute-forcing.
*   Example: `sp_inv_` + `secure_random_string`.

### 4.2 Expiration Logic
*   A background job (`InvoiceCleanupJob`) runs hourly to mark `INITIATED` or `VIEWED` invoices as `EXPIRED` if `Instant.now() > expiresAt`.

### 4.3 Payment Initiation
When a customer clicks "Pay" on the public invoice page:
1.  Verify `status` is `INITIATED` or `VIEWED`.
2.  Verify `expiresAt` is not in the past.
3.  Call `payment-core.InitiatePaymentUseCase` with:
    *   `amount`: `invoice.totalAmount`.
    *   `metadata`: `{"invoice_id": "...", "source": "INVOICE"}`.
4.  Store the returned `paymentId` in the `Invoice` entity.

## 5. Implementation Details

### 5.1 QR Code Generation
*   Use a library like `zxing` or an external service to generate a QR code pointing to `https://request.shadhinpay.com/invoices/v1/{slug}`.

### 5.2 Public Page Security
*   The public endpoint must be rate-limited by IP to prevent scrapers.
*   No sensitive merchant data (like credentials) should be exposed; only `Business.displayName` and `Business.logoUrl` from the `provisioning` module.

## 6. Testing Strategy
*   **State Transition Test:** Verify that an `EXPIRED` invoice cannot be paid.
*   **Integrity Test:** Ensure that the `PaymentCompletedEvent` correctly updates the specific invoice linked in its metadata.
*   **Slug Uniqueness:** Verify that generating 1M invoices does not result in slug collisions.
