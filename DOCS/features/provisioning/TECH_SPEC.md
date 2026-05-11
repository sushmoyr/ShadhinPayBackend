# Tech Spec - Business Provisioning & API Management (conflux-provisioning)

## 1. Architecture
Follows Hexagonal Architecture. This module provides the "Context" (Business rules + Credentials) for every API request.

## 2. Domain Model & Schema

### 2.1 Entities

#### `Business`
*   `id`: UUID
*   `merchantId`: UUID (References `User.id` in Identity module)
*   `name`: String
*   `displayName`: String
*   `logoUrl`: String
*   `status`: Enum (`ACTIVE`, `INACTIVE`)
*   `webhookUrl`: String
*   `webhookSecret`: String (Encrypted)

#### `VendorConfig`
Configuration per MFS provider for a specific business.
*   `id`: UUID
*   `businessId`: UUID (FK to Business)
*   `vendor`: Enum (`BKASH`, `NAGAD`, `ROCKET`, `UPAY`, `PATHAO`, `MCASH`, `STRIPE`)
*   `mode`: Enum (`PARTNER`, `CUSTOM`)
*   `credentials`: JSONB (Encrypted) - *e.g., {"appKey": "...", "appSecret": "..."}*

#### `ApiKey`
*   `id`: UUID
*   `businessId`: UUID (FK to Business)
*   `keyHash`: String (Hashed representation for DB lookups)
*   `keyPrefix`: String (e.g., `sp_live_` or `sp_test_`)
*   `environment`: Enum (`TEST`, `LIVE`)
*   `lastUsedAt`: Instant
*   `expiresAt`: Instant

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `CreateBusinessUseCase`: Creates a new business profile.
*   `ConfigureVendorUseCase`: Sets mode and stores encrypted credentials.
*   `GenerateApiKeyUseCase`: Creates a new key, returns the **plain text** only once.
*   `ValidateApiKeyUseCase`: Used by the Global Gateway Filter to authorize requests.
*   `UpdateWebhookUseCase`: Updates URL and rotates signing secret.

### 3.2 Event Listeners (Spring Modulith)
*   `on(MerchantVerifiedEvent event)`: Automatically triggers `CreateBusinessUseCase` to set up the merchant's first "Default Business."

## 4. Business Logic Rules

### 4.1 Credential Encryption
*   All `VendorConfig.credentials` must be encrypted using **AES-256-GCM**.
*   The encryption key should be unique per Business or derived from a Master Key + Business Salt.
*   **Plain-text leak prevention:** Mapper must *never* include the `credentials` field in public DTOs.

### 4.2 API Key Generation
1.  Generate a 32-character random string.
2.  Prefix it: `sp_live_` + string.
3.  Store the **SHA-256 Hash** in `ApiKey.keyHash`.
4.  Store the **Prefix** and **Last 4 Chars** for UI identification.
5.  Return the **Full Plain Text** to the user (once).

### 4.3 Webhook Integrity & Transport
*   **Mandatory HTTPS:** Webhook URLs provided by merchants must use `https://`.
*   **HMAC Signing:** All payloads must be signed using `HMAC-SHA256` with the `webhookSecret`.
*   **Integrity Header:** The resulting signature must be sent in the `X-PGW-Signature` header.
*   **Data Integrity:** This allows merchants to verify that the payload was not tampered with during transit and that it originated from ConfluxPay.

## 5. Implementation Details

### 5.1 Caching Strategy
*   `ApiKey` validation and `VendorConfig` lookups are high-frequency.
*   Use **Redis** to cache `keyHash -> {businessId, environment}`.
*   Cache eviction must occur when a business is blocked or a key is rotated.

### 5.2 Isolation Control
*   Every request to `payment-core` will carry the `businessId` and `environment` extracted by this module.
*   Data access in all other modules MUST be filtered by `businessId`.

## 6. Testing Strategy
*   **Security Audit:** Verify that `VendorConfig` entries in the DB are unreadable without the KMS key.
*   **Rotation Test:** Verify that once an API key is rotated, the old key immediately returns 401.
*   **Multi-Tenancy Test:** Verify that an API key for Business A cannot access data for Business B.
