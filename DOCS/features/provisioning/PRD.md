# PRD - Business Provisioning & API Management

## 1. Purpose
To enable verified Merchants to manage their business entities, configure payment vendor credentials (bKash, Nagad, etc.), and securely manage the API keys and webhooks required for integration.

## 2. Target Audience
*   **Merchants:** To set up their businesses and obtain integration credentials.
*   **Developers:** To manage Test vs. Live keys and configure webhooks.
*   **System (Payment Core):** To retrieve business settings and vendor credentials during transaction processing.

## 3. User Stories

### 3.1 Business Management
*   **As a Merchant**, I want to create multiple business profiles (e.g., "Main Shop" and "Delivery Service") under my single verified account.
*   **As a Merchant**, I want to configure separate contact details and logos for each business.

### 3.2 Credential Management (Custom Mode)
*   **As a Merchant**, I want to securely input my own MFS credentials (e.g., bKash App Key, Nagad Merchant ID) for businesses where I have direct MFS relationships.
*   **As a Merchant**, I want these credentials to be encrypted and never shown back to me in plain text.

### 3.3 API Keys & Webhooks
*   **As a Developer**, I want to generate separate API keys for `TEST` and `LIVE` environments.
*   **As a Developer**, I want to configure a Webhook URL and a Signing Secret so that my system is notified of payment status changes.
*   **As a Developer**, I want to rotate my API keys if they are compromised.

## 4. Functional Requirements

### 4.1 Multi-Tenant Business Model
*   Each `Merchant` can own multiple `Business` entities.
*   Each `Business` has its own:
    *   Display Name & Logo.
    *   Vendor Configuration (bKash, Nagad, etc.).
    *   API Credentials.
    *   Webhook Settings.

### 4.2 Vendor Configuration (Dual Mode)
For each supported MFS provider (bKash, Nagad, Rocket, etc.), a business can be in one of two modes:
1.  **PARTNER MODE (Default):** Uses ConfluxPay's aggregate credentials. No merchant-provided setup required.
2.  **CUSTOM MODE:** Merchant provides their own credentials (App Key, Secret, Merchant ID).

### 4.3 API Key Lifecycle
*   **Scope:** Keys are scoped to a specific `Business` and an `Environment` (Test/Live).
*   **Structure:** `sp_live_...` or `sp_test_...` followed by a cryptographically secure random string.
*   **Storage:** Keys must be hashed in the database (like passwords); only the last 4 characters should be visible in the UI.

### 4.4 Webhook Configuration
*   **URL:** A valid HTTPS endpoint provided by the merchant.
*   **Signing Secret:** A platform-generated secret used to sign HMAC-SHA256 payloads.

## 5. Non-Functional Requirements
*   **Security:** Merchant-provided MFS credentials must be encrypted at rest using AES-256 (KMS-backed).
*   **Performance:** Business and Credential lookups must be sub-50ms (cached) as they are on the "Critical Path" of every payment.
*   **Isolation:** A business's API key must *never* be able to initiate a payment for another business, even within the same merchant account.

## 6. Acceptance Criteria
*   New businesses are automatically created with `PARTNER MODE` enabled for all vendors.
*   API keys for `LIVE` environments cannot be generated if the Merchant is not in `ACTIVE` status.
*   Changing a Webhook URL triggers a "test ping" to the new endpoint. The webhook will follow an event pattern where it will send events. When a URL is changes, a PING event will be sent and the receiver will return a 200. 
