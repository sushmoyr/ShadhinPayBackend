# Tech Spec - Identity & Merchant Onboarding (shadhinpay-identity)

## 1. Architecture
Follows the UseCase-driven Hexagonal Architecture. This module is the centralized authority for Authentication and Identity Management.

## 2. Domain Model & Schema

### 2.1 Entities

#### `User` (The Identity)
The core authentication entity.
*   `id`: UUID
*   `identifier`: String (Unique, Indexed) - e.g., "01712345678" or "admin@shadhinpay.com"
*   `identifierType`: Enum (`PHONE`, `EMAIL`, `USERNAME`)
*   `passwordHash`: String
*   `userType`: Enum (`MERCHANT`, `ADMIN`)
*   `status`: Enum (`ACTIVE`, `INACTIVE`, `BLOCKED`)
*   `mfaSecret`: String (Encrypted)
*   `lastLoginAt`: Instant

#### `MerchantProfile`
Specific business data for Merchants.
*   `id`: UUID
*   `userId`: UUID (FK to User)
*   `fullName`: String
*   `onboardingStatus`: Enum (`REGISTERED`, `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`)
*   `kycData`: JSONB (Stores NID, Trade License metadata)

#### `AdminProfile`
Internal metadata for platform managers.
*   `id`: UUID
*   `userId`: UUID (FK to User)
*   `department`: String
*   `employeeId`: String

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `AuthenticateUserUseCase`: Handles login. Detects `identifierType` via Regex (e.g., `^01[3-9]\d{8}$` for BD Phone) before querying the `UserRepository`.
*   `RegisterMerchantUseCase`: Creates both `User` (type MERCHANT, identifierType PHONE) and `MerchantProfile`.
*   `VerifyMerchantUseCase`: (Admin only) Updates `MerchantProfile.onboardingStatus`.

### 3.2 Outbound Events (Spring Modulith)
*   `MerchantVerifiedEvent`: Published when `onboardingStatus` moves to `VERIFIED`.
*   `UserBlockedEvent`: Published when `User.status` moves to `BLOCKED`.

## 4. Business Logic Rules

### 4.1 Authentication Logic
1.  Receive `login(identifier, password)`.
2.  Run **Regex Detection** on `identifier`:
    *   Starts with digit/BD prefix? -> `identifierType = PHONE`.
    *   Contains `@`? -> `identifierType = EMAIL`.
    *   Otherwise -> `identifierType = USERNAME`.
3.  Query `User` where `identifier = :identifier AND identifierType = :type`.
4.  Verify `BCrypt` password hash.

### 4.2 Security
*   **Encryption:** `MerchantProfile.kycData` and `User.mfaSecret` must be encrypted at rest using the `common` encryption utils.
*   **Soft Delete:** Handled via `AuditableAndSoftDeletable` and explicit `AndDeletedFalse` repository queries.

## 5. Testing Strategy
*   **Regex Test:** Unit test the `IdentifierDetector` utility with various inputs (valid/invalid BD phones, emails, etc.).
*   **Polymorphic Auth Test:** Ensure a Merchant can log in with Phone and an Admin with Email using the same flow.
