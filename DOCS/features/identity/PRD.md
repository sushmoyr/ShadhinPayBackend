# PRD - Identity & Merchant Onboarding

## 1. Purpose
To provide a secure and moderated entry point for Merchants to join the ConfluxPay platform. This system manages the lifecycle of a Merchant from initial signup to full business activation, including regulatory compliance (KYC/KYB) and administrative oversight.

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

### 3.3 Super Admin Stories
*   **As a Super Admin**, I want to log in to a dedicated admin console using my email and password.
*   **As a Super Admin**, I want to create new admin accounts (Viewer / Manager / Super) so the platform team can self-serve onboarding.
*   **As a Super Admin**, I want to change another admin's tier or disable their account if they leave the team.
*   **As a Super Admin**, I expect the system to refuse to start on a fresh database until a bootstrap super-admin identity is provided via configuration, so no environment is ever silently un-administered.

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
*   **Storage:** Documents must be stored securely (using the `conflux-secrets` or `library-storage` logic).

### 4.3 Role-Based Access Control (RBAC)
Two coarse `userType` values gate the high-level surface; a finer `adminTier` on
`AdminProfile` distinguishes admin capability. Higher tiers strictly inherit the
authorities of lower tiers (a MANAGER can do everything a VIEWER can; a SUPER
can do everything a MANAGER can).

| Role | `userType` | `adminTier` | Capabilities |
|---|---|---|---|
| **ROLE_MERCHANT** | `MERCHANT` | — | Own dashboard, business settings, API key management, reports |
| **ROLE_ADMIN_VIEWER** | `ADMIN` | `VIEWER` | Read-only access to platform stats, merchant queue, ledger journals |
| **ROLE_ADMIN_MANAGER** | `ADMIN` | `MANAGER` | Verify/reject merchants, block/unblock users, manage businesses, + all VIEWER rights |
| **ROLE_SUPER_ADMIN** | `ADMIN` | `SUPER` | Create/update/disable other admins, change global configs, + all MANAGER rights |

**Bootstrap (super-admin seed).** Exactly one `SUPER` admin MUST exist before
the platform can be administered. On every application boot, the system reads
`SUPER_ADMIN_IDENTIFIER` and `SUPER_ADMIN_PASSWORD` from configuration and:

1. If the env vars are absent **and** no SUPER admin exists in the database, the
   application MUST fail-fast at startup. There is no path to a running cluster
   without at least one super admin.
2. If a SUPER admin with the given identifier does not exist, create one
   (BCrypt the password, `User.status = ACTIVE`, `AdminProfile.adminTier = SUPER`,
   `AdminProfile.department = "Platform"`, `employeeId = "SUPER-0001"`).
3. If a SUPER admin with the given identifier already exists, the seed is a no-op
   unless the configured password's BCrypt hash differs from the stored hash, in
   which case the stored hash is updated (env-driven rotation).

**Promotion / demotion rules.**
*   Only a SUPER admin may create new admins of any tier or change another
    admin's tier.
*   The system MUST refuse any operation that would leave zero SUPER admins
    (self-demotion of the last super admin is rejected with a clear error).
*   Disabling an admin (`User.status = BLOCKED`) revokes their access
    immediately on the next request; tokens are not separately revoked because
    the auth filter consults `User.status` on every call.

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
*   A fresh database boot fails fast with a clear error if `SUPER_ADMIN_IDENTIFIER` / `SUPER_ADMIN_PASSWORD` are absent; once set, exactly one super admin row is created and subsequent boots are idempotent.
*   Admin endpoints reject requests from `MERCHANT` users with `403`; `VIEWER` admins can read but cannot mutate; `MANAGER` admins can mutate merchants/businesses but cannot manage other admins; `SUPER` admins can manage other admins.
*   A super admin cannot demote themselves if doing so would leave zero super admins in the system.
