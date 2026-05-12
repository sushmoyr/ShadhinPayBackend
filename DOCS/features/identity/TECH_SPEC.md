# Tech Spec - Identity & Merchant Onboarding (conflux-identity)

## 1. Architecture
Follows the UseCase-driven Hexagonal Architecture. This module is the centralized authority for Authentication and Identity Management.

## 2. Domain Model & Schema

### 2.1 Entities

#### `User` (The Identity)
The core authentication entity.
*   `id`: UUID
*   `identifier`: String (Unique, Indexed) - e.g., "01712345678" or "admin@conflux.com"
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
*   `employeeId`: String (Unique)
*   `adminTier`: Enum (`VIEWER`, `MANAGER`, `SUPER`) — NOT NULL. Refines `User.userType = ADMIN` into the three role tiers from `PRD §4.3`. Higher tiers strictly inherit lower-tier authorities.

> **Note:** `User.userType` is intentionally coarse (`MERCHANT` vs `ADMIN`) and remains the discriminator for the high-level surface (which login flow, which controller surface). The tier is a refinement that only exists when `userType = ADMIN`, so it lives on `AdminProfile` rather than `User` — keeping merchant rows free of an always-null `tier` column.

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   `AuthenticateUserUseCase`: Handles login. Detects `identifierType` via Regex (e.g., `^01[3-9]\d{8}$` for BD Phone) before querying the `UserRepository`. Returns a token whose claim set includes `userType` and (when `userType = ADMIN`) the resolved `adminTier`. Rejects `BLOCKED` users with the same generic credentials error as a missing user.
*   `RegisterMerchantUseCase`: Creates both `User` (type MERCHANT, identifierType PHONE) and `MerchantProfile`.
*   `VerifyMerchantUseCase`: (Admin only — requires `ADMIN_MANAGER`+) Updates `MerchantProfile.onboardingStatus`.
*   `CreateAdminUseCase`: (Requires `SUPER_ADMIN`) Creates a `User` (type ADMIN, identifierType EMAIL by convention) + `AdminProfile` with the requested `adminTier`. Rejects duplicate identifiers and rejects creation of a SUPER admin when the caller is not already SUPER (the bootstrap path uses `SuperAdminBootstrap`, not this use case).
*   `UpdateAdminTierUseCase`: (Requires `SUPER_ADMIN`) Changes another admin's `adminTier`. Rejects the change if applying it would leave zero SUPER admins in the system (count check inside the same transaction).
*   `DisableAdminUseCase`: (Requires `SUPER_ADMIN`) Sets `User.status = BLOCKED` on another admin. Rejects self-disable; rejects disabling the last remaining SUPER admin (same count check).

### 3.2 Outbound Events (Spring Modulith)
*   `MerchantVerifiedEvent`: Published when `onboardingStatus` moves to `VERIFIED`.
*   `UserBlockedEvent`: Published when `User.status` moves to `BLOCKED`. Reused for admin disable — downstream consumers (e.g., `provisioning` API-key revocation) ignore events where the target is an admin user, since merchants are the only API-key holders.

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

### 4.3 Authority Resolution
The auth filter populates `SecurityContextHolder` with Spring authorities derived
from `User.userType` and (for admins) `AdminProfile.adminTier`. Higher tiers
inherit lower-tier authorities so `@PreAuthorize("hasAuthority('ADMIN_VIEWER')")`
on a list endpoint admits MANAGER and SUPER callers as well.

| `userType` | `adminTier` | Granted authorities |
|---|---|---|
| `MERCHANT` | (n/a) | `MERCHANT` |
| `ADMIN` | `VIEWER` | `ADMIN_VIEWER` |
| `ADMIN` | `MANAGER` | `ADMIN_VIEWER`, `ADMIN_MANAGER` |
| `ADMIN` | `SUPER` | `ADMIN_VIEWER`, `ADMIN_MANAGER`, `SUPER_ADMIN` |

Two filters coexist in the security chain and are mutually exclusive per request:

*   **`ApiKeyAuthFilter`** (already shipped, Wave B 8c) — resolves merchant API
    keys against `provisioning.GetBusinessByApiKeyUseCase`, grants `MERCHANT`,
    exposes the resolved `businessId` as a request attribute.
*   **`JwtAuthorizationFilter`** (Wave D) — resolves `Authorization: Bearer <jwt>`
    against the token issued by `AuthenticateUserUseCase`. Loads the `User` row
    (and `AdminProfile` if `userType = ADMIN`) on every request to honor
    live `status` changes; grants the authorities per the table above. Skips
    requests whose `Authorization` header is the API-key shape (resolved by the
    other filter) so the two never both authenticate the same request.

Public endpoints (`/api/v1/auth/login`, `/api/v1/merchant/register`,
`/api/v1/payments/callback/**`, actuator health, OpenAPI docs) are whitelisted
in `SecurityConfig` and skip both filters.

### 4.4 Super Admin Bootstrap
A `SuperAdminBootstrap` Spring bean (`@Component` implementing
`ApplicationRunner`, ordered after Flyway) runs once per boot and enforces the
seed contract from `PRD §4.3`:

1. Read `SUPER_ADMIN_IDENTIFIER` and `SUPER_ADMIN_PASSWORD` from configuration
   (`conflux.identity.super-admin.identifier`, `…password`, both env-var-driven).
2. Count existing `AdminProfile` rows with `adminTier = SUPER` and
   `User.status = ACTIVE`.
3. If both env vars are blank **and** the count is zero, throw
   `IllegalStateException("Refusing to start: no SUPER admin present and no
   bootstrap credentials configured")` — this halts the JVM with a non-zero
   exit code via Spring's default failure handling.
4. If the configured identifier does not exist as a user, create
   `User` (`userType=ADMIN`, `identifierType` detected via `IdentifierDetector`,
   `passwordHash = BCrypt(password)`, `status=ACTIVE`) and
   `AdminProfile` (`adminTier=SUPER`, `department="Platform"`,
   `employeeId="SUPER-0001"`) in a single transaction.
5. If the configured identifier already exists and its stored BCrypt hash does
   not match the configured password, update the hash in place (env-driven
   rotation). Log at INFO that a rotation occurred — never log the password.
6. If the configured identifier already exists and the password matches, do
   nothing.

The bootstrap is idempotent and safe to re-run on every boot. It does NOT use
`CreateAdminUseCase` — that use case requires an authenticated SUPER caller,
which is a chicken-and-egg problem on a fresh database. The bootstrap writes
directly via repositories.

**Why a bean, not a Flyway migration:** BCrypt hashing must happen in the JVM
(Flyway has no access to Spring Security primitives), and the bootstrap must be
idempotent across many boots — Flyway runs each migration exactly once.

## 5. Testing Strategy
*   **Regex Test:** Unit test the `IdentifierDetector` utility with various inputs (valid/invalid BD phones, emails, etc.).
*   **Polymorphic Auth Test:** Ensure a Merchant can log in with Phone and an Admin with Email using the same flow.
*   **Authority Resolution Test:** `@WebMvcTest` per admin tier covering the inheritance matrix in §4.3 — a VIEWER token gets 200 on `GET /admin/merchants` and 403 on `POST /admin/merchants/{id}/verify`; a MANAGER token gets 200 on both; a SUPER token gets 200 on both **plus** `POST /admin/admins`.
*   **Super Admin Bootstrap Tests** (Testcontainers Postgres):
    *   **Fresh DB, no env vars** → application context fails to start with the expected `IllegalStateException`.
    *   **Fresh DB, env vars set** → exactly one SUPER admin exists after boot; identifier and hash match config.
    *   **Re-boot with same env vars** → no new row, hash unchanged, log emits "idempotent" message.
    *   **Re-boot with different password** → same row, hash updated, log emits "rotated" message.
    *   **Pre-existing SUPER admin, env vars cleared** → application boots successfully (no fail-fast when a SUPER admin already exists).
*   **Last-SUPER Guard Tests:** `UpdateAdminTierUseCase` and `DisableAdminUseCase` integration tests with exactly one SUPER admin in the DB — both must reject the operation and the DB row must be unchanged on rollback.
