# PRD - Risk & Fraud Engine (conflux-risk)

## 1. Purpose
The Risk & Fraud Engine is the "Guardian" of ConfluxPay. It protects the platform, its merchants, and MFS partners by identifying, flagging, and mitigating suspicious activities. It provides a real-time pre-flight scoring system for transactions and a comprehensive rule management dashboard for platform managers.

## 2. Target Audience
*   **Platform Managers (Admins):** To manage risk rules, monitor suspicious activities, and perform manual reviews.
*   **Payment Core:** To obtain a risk score and decision before processing a transaction.
*   **Merchants:** To be notified of flagged transactions and manage their own risk profiles (within limits).

## 3. User Stories

### 3.1 Platform Manager Stories
*   **As an Admin**, I want to define dynamic rules (e.g., "Block transactions > 50,000 BDT for new merchants") without deploying new code.
*   **As an Admin**, I want to see a queue of "Flagged" transactions that require manual review.
*   **As an Admin**, I want to globally blacklist a specific phone number or IP address across the entire platform.
*   **As an Admin**, I want to configure custom velocity limits for specific merchants based on their trust level.

### 3.2 System Stories
*   **As the Payment Core**, I want to receive a risk score (0-100) and a recommended action (Allow, Flag, Block) for every transaction initiation.
*   **As the System**, I want to automatically block transactions that match high-confidence fraud patterns (e.g., known blacklist match).

## 4. Functional Requirements

### 4.1 Dynamic Rule Engine
*   Support for a dynamic evaluation engine (e.g., Spring Expression Language - SpEL or Drools).
*   Admins can create rules based on:
    *   Transaction amount, currency, and vendor.
    *   Merchant age, category, and historical success rate.
    *   Customer identifier (Phone/Email) and IP address.
    *   Time of day and frequency (Velocity).

### 4.2 Velocity Monitoring
*   Track and enforce limits at multiple levels:
    *   **Per Merchant:** Configurable max transactions/volume per minute/hour/day.
    *   **Per Customer MSISDN:** Prevent account takeover or "carding" attempts.
    *   **Per IP Address:** Mitigate bot-driven attacks.

### 4.3 Blacklist/Whitelist Management
*   Global registry for blocked entities:
    *   Phone Numbers (MSISDN).
    *   Email Addresses.
    *   IP Ranges.
    *   Specific Business Identifiers.

### 4.4 Case Management (Manual Review)
*   Transactions that exceed a "Flag" threshold (but not "Block") move to a `PENDING_RISK` state.
*   Platform Managers must either "Approve" (releasing it to `payment-core`) or "Reject" (cancelling it) within a specific timeframe.

## 5. Non-Functional Requirements
*   **Latency:** The pre-flight risk evaluation must be sub-50ms to avoid degrading the checkout experience.
*   **Reliability:** Use high-performance caching (Redis) for velocity counters and blacklist lookups.
*   **Observability:** Every risk decision must be logged with the specific rules that were triggered for auditability.

## 6. Acceptance Criteria
*   Transactions matching a "Hard Block" rule are immediately rejected with a `403 Forbidden` or specific risk error code.
*   Platform managers can update a merchant's velocity limit and see it take effect within seconds.
*   The system correctly aggregates usage across multiple businesses for a single merchant account if the rule is scoped to the `Account`.
*   All risk decisions are visible in the Transaction History for internal audit.
