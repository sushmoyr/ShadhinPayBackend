# PRD - MFS Adapter Library (shadhinpay-adapters)

## 1. Purpose
To provide a unified, abstract layer for communicating with diverse Mobile Financial Services (MFS) and card payment providers (bKash, Nagad, Rocket, Stripe, etc.). This module isolates the specific API quirks, authentication flows, and data formats of each vendor from ShadhinPay's core business logic.

## 2. Target Audience
*   **Developers:** Who need to add new payment providers without touching the `payment-core`.
*   **System (Payment Core):** To execute payments and query statuses through a single interface.
*   **Platform Managers:** To understand why a specific vendor might be failing (via unified error codes).

## 3. User Stories
*   **As a Developer**, I want to implement a new MFS provider by just implementing a single Java interface.
*   **As a System**, I want to initiate a bKash payment using the same method call as a Nagad payment.
*   **As a System**, I want to automatically poll the vendor API if a customer drops off during checkout to ensure the ledger remains accurate.
*   **As a Support Manager**, I want to see a clear, unified error message regardless of whether the failure happened on bKash or Stripe.

## 4. Functional Requirements

### 4.1 The `PaymentProvider` Contract
Every adapter must implement a common interface with the following methods:
*   `initiate(PaymentRequest)`: Returns a redirect URL or a payment token.
*   `queryStatus(VendorTransactionId)`: Polls the vendor for the final state.
*   `refund(RefundRequest)`: Initiates a reversal.
*   `verifyWebhook(IncomingWebhook)`: Validates the vendor's signature.

### 4.2 Centralized Token Management
*   The system must maintain a `shadhinpay-token-service` (part of the library/infrastructure layer) to handle the lifecycle of bKash/Nagad session tokens.
*   Tokens must be cached in **Redis** with TTLs matching the vendor's expiration policy.

### 4.3 Automated Reconciliation (Polling)
*   The system must support "Active Polling." If a transaction remains in `PENDING` status for more than 5 minutes, the adapter's `queryStatus` method is triggered automatically by a background worker.

### 4.4 Error Mapping
*   Vendor-specific error codes (e.g., "bKash 2023: Insufficient Balance") must be mapped to ShadhinPay's `ErrorCode.INSUFFICIENT_FUNDS`.

## 5. Non-Functional Requirements
*   **Isolation:** Each adapter must use a dedicated **OkHTTP Client** instance to prevent connection pool exhaustion from one vendor affecting others.
*   **Resiliency:** Use **Circuit Breakers** (Resilience4j) for each adapter to fail-fast if a vendor's API is down.
*   **Observability:** Every outgoing request must be logged with the `transaction_id` for debugging.

## 6. Acceptance Criteria
*   Adding a new MFS provider requires zero changes to the `shadhinpay-payment-core` module.
*   The system can recover the state of a "lost" transaction via automated polling.
*   Vendor credentials from `shadhinpay-provisioning` are correctly applied to the outgoing requests.
