# PRD - Identity & Merchant Onboarding

## 1. Purpose
To provide a secure and moderated entry point for Merchants to join the ShadhinPay platform. This system manages the lifecycle of a Merchant from initial signup to full business activation, including regulatory compliance (KYC/KYB) and administrative oversight.

## 2. Target Audience
*   **Merchants:** Business owners seeking to integrate payment gateways.
*   **Platform Managers (Admins):** Staff responsible for verifying documents and mitigating risk.
*   **System:** Other modules (Payments, Invoices) that require an "Active" status to function.

## 3. User Stories

### 3.1 Merchant Stories
*   **As a Merchant**, I want to register an account using my email and phone number.
*   **As a Merchant**, I want to upload my Trade License and NID so that I can be verified to process payments.
*   **As a Merchant**, I want to see my current onboarding status (Pending/Verified/Rejected) on my dashboard.
*   **As a Merchant**, I want to set up 2FA to secure my financial account.

### 3.2 Platform Manager Stories
*   **As a Platform Manager**, I want to see a queue of merchants waiting for verification.
*   **As a Platform Manager**, I want to review a merchant's uploaded documents and either Approve or Reject them with a reason.
*   **As a Platform Manager**, I want to Block/Suspend a merchant if they violate platform policies.

## 4. Functional Requirements

### 4.1 Merchant Lifecycle (State Machine)
The system must enforce the following states for a Merchant Account:
1.  **REGISTERED:** Account created, email/phone verified, but no business data yet.
2.  **PENDING_VERIFICATION:** Documents (NID, Trade License) uploaded and submitted for review.
3.  **VERIFIED:** Documents approved by Platform Manager.
4.  **ACTIVE:** Merchant has completed setup (e.g., added at least one business). **Required for API usage.**
5.  **REJECTED:** Verification failed. Merchant must re-upload/correct data.
6.  **SUSPENDED/BLOCKED:** Account disabled by Admin due to risk/abuse.

### 4.2 Document Management (KYB/KYC)
*   **Mandatory Documents:**
    *   **NID:** Front and Back images (or PDF).
    *   **Trade License:** Clear image/PDF.
    *   **TIN Certificate:** (Optional/Tiered).
*   **Storage:** Documents must be stored securely (using the `shadhinpay-secrets` or `library-storage` logic).

### 4.3 Role-Based Access Control (RBAC)
*   **ROLE_MERCHANT:** Access to own dashboard, business settings, and reports.
*   **ROLE_ADMIN_VIEWER:** Read-only access to platform stats.
*   **ROLE_ADMIN_MANAGER:** Ability to verify/reject merchants.
*   **ROLE_SUPER_ADMIN:** Full access, including managing other Admins and global configs.

### 4.4 Multi-Factor Authentication (MFA)
*   Support for TOTP (Google Authenticator) or SMS-based OTP for login and sensitive actions (like API key generation).

## 5. Non-Functional Requirements
*   **Privacy:** NID numbers and PII must be encrypted at rest.
*   **Audit Trail:** Every status change (e.g., Pending -> Verified) must be logged with the ID of the actor who performed it.
*   **Scalability:** The verification queue should be searchable and paginated for high volumes.

## 6. Acceptance Criteria
*   A Merchant cannot generate API keys until their status is `ACTIVE`.
*   Platform Managers receive a notification when a new merchant submits documents.
*   Rejection requires a mandatory "Reason" string sent to the merchant via email/SMS.
*   Blocking a merchant immediately revokes their ability to initiate new transactions.
