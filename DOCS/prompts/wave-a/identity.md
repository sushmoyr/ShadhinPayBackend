# Phase 1 Wave A — `identity` module prompts

> **Branch:** `phase-1/identity` — run all three sub-prompts sequentially in the same git worktree on the same branch. Each sub-prompt commits independently.
> **Scope:** centralized authentication + identity management. Owns `User`, `MerchantProfile`, `AdminProfile`. Publishes `MerchantVerifiedEvent`, `UserBlockedEvent`.
> **Read first (every sub-prompt):** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md) — it lists the cross-cutting decisions (per-module routes, merge-train OpenAPI, pre-approved deps).

Sub-prompts:
1. [1a — foundation + auth](#prompt-1a--identity-foundation--auth)
2. [1b — KYC + admin lifecycle + events](#prompt-1b--identity-kyc--admin-lifecycle--events)
3. [1c — MFA + coverage push](#prompt-1c--identity-mfa--coverage-push)

---

## Prompt 1a — identity foundation + auth

```
You are starting Phase 1 Wave A on the `shadhinpay-identity` module. This is the FIRST of THREE sequential sub-prompts (1a → 1b → 1c) that build the module on branch `phase-1/identity`. After this sub-prompt commits, the next session runs Prompt 1b on the same branch.

Your sub-scope: Flyway schema, entities + repositories, the User-encryption AttributeConverter, and the merchant register + login flow with its REST controller. KYC submission, admin verification, blocking, MFA — those come in 1b and 1c.

READ FIRST (in order)
- ARCHITECTURE.md (full file)
- DEVELOPMENT_WORKFLOW.md §4.1 (Wave A), §7.2 (definition of done)
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md "Cross-cutting decisions" section (top of file)
- shadhinpay-identity/CLAUDE.md
- DOCS/features/identity/PRD.md §1–§4.1 (lifecycle), §4.4 (MFA — for awareness only)
- DOCS/features/identity/TECH_SPEC.md (full)
- shadhinpay-common/src/main/java/com/shadhinpay/common/crypto/AesGcmCipher.java (you'll wrap this in an AttributeConverter)
- shadhinpay-common/src/main/java/com/shadhinpay/common/util/IdentifierDetector.java + IdentifierType.java

WORK ONLY IN
- shadhinpay-identity/src/main/java/com/shadhinpay/identity/{entity,repository,usecase,usecase.impl,controller,dto,mapper,constant}/...
- shadhinpay-identity/src/test/...
- shadhinpay-application/src/main/resources/db/migration/V1001__identity_schema.sql
- shadhinpay-application/src/test/java/com/shadhinpay/identity/...

DO NOT TOUCH
- shadhinpay-common/ (read-only)
- shadhinpay-identity/src/main/java/com/shadhinpay/identity/events/ (records locked in Phase 0)
- DOCS/contracts/openapi.json (merge train regenerates this)
- common.constant.Routes (per cross-cutting decision #1, use IdentityRoutes instead)
- Root pom.xml (no deps needed in this sub-prompt)
- Any other shadhinpay-{feature}/

DELIVERABLES

1. Flyway `V1001__identity_schema.sql`:
   - `users` (id UUID PK, identifier VARCHAR(255), identifier_type VARCHAR(16), password_hash VARCHAR(72), user_type VARCHAR(16), status VARCHAR(16), mfa_secret TEXT NULL, last_login_at TIMESTAMPTZ NULL, audit + soft-delete columns).
   - Partial unique index `(identifier, identifier_type) WHERE deleted = false`.
   - `merchant_profiles` (id, user_id UNIQUE FK, full_name, onboarding_status, kyc_data TEXT NULL, audit columns).
   - `admin_profiles` (id, user_id UNIQUE FK, department, employee_id UNIQUE, audit columns).
   - All FKs ON DELETE RESTRICT.

2. Entities (`com.shadhinpay.identity.entity`):
   - `User extends AuditableAndSoftDeletable` — `@Enumerated(STRING)` on every enum field. `mfaSecret` is encrypted via a JPA `AttributeConverter` you write here (see deliverable 3); leave the field nullable for 1c to populate.
   - `MerchantProfile extends Auditable` — `kycData` encrypted via the same converter approach (purpose=`kyc-data`); leave nullable for 1b.
   - `AdminProfile extends Auditable`.
   - Enums: local `IdentifierType` (PHONE/EMAIL/USERNAME — match values with `common.util.IdentifierType` exactly so a static cast or simple mapper works), `UserType` (MERCHANT/ADMIN), `UserStatus` (ACTIVE/INACTIVE/BLOCKED), `OnboardingStatus` (REGISTERED/PENDING_VERIFICATION/VERIFIED/REJECTED).

3. JPA AttributeConverters (`com.shadhinpay.identity.entity.converter`):
   - `EncryptedStringConverter` — abstract base or two concrete classes (`MfaSecretConverter` purpose=`mfa-secret`, `KycDataConverter` purpose=`kyc-data`). Each takes the AES key purpose as a constructor arg / annotation. Use `common.crypto.AesGcmCipher`. The converter must be `@Converter(autoApply = false)` so we apply it explicitly per field via `@Convert(converter = MfaSecretConverter.class)`.

4. Repositories (`com.shadhinpay.identity.repository`):
   - `UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User>`:
     - `Optional<User> findByIdentifierAndIdentifierTypeAndDeletedFalse(String, IdentifierType)`
     - `boolean existsByIdentifierAndIdentifierTypeAndDeletedFalse(String, IdentifierType)`
   - `MerchantProfileRepository`, `AdminProfileRepository` — basic findByUserIdAndDeletedFalse.

5. Use-case interfaces + impls (`usecase` + `usecase.impl`):
   - `RegisterMerchantUseCase` — input `RegisterMerchantRequest`, output `MerchantOnboardingDto`. Step 1 detect identifier type via `common.util.IdentifierDetector`. Step 2 reject if `existsByIdentifierAndIdentifierType`. Step 3 BCrypt the password (use Spring Security's `BCryptPasswordEncoder`, default cost 10). Step 4 create User (`status=ACTIVE`) and MerchantProfile (`onboardingStatus=REGISTERED`) atomically inside `@Transactional`. Step 5 return `MerchantOnboardingDto` (no password, no kyc).
   - `AuthenticateUserUseCase` — input `LoginRequest(identifier, password)`, output `LoginResponse(authToken, userId, userType)`. Step 1 regex detect identifier type. Step 2 lookup; if not found → `UnauthorizedException("Invalid credentials")` with log.warn. Step 3 BCrypt verify; on mismatch → SAME exception, SAME message (no enumeration leak). Step 4 reject if `status != ACTIVE`. Step 5 update `lastLoginAt`. Step 6 issue auth token: build a stub `AuthToken` record with userId + userType + issuedAt + expiresAt and HMAC-sign via `common.crypto.HmacSigner` (signing key from `application.yml` config `shadhinpay.auth.token-secret`, env-injected). Return Base64URL-encoded `payload.signature`. Wave B will replace this with full JWT — for now this stub is the contract. Document this clearly in Javadoc.

6. REST controllers (`com.shadhinpay.identity.controller`):
   - `MerchantAuthController` interface + `Impl` per ARCHITECTURE.md §5. Endpoints:
     - `POST /api/v1/merchant/register` → 201, `ApiResult<MerchantOnboardingDto>`
     - `POST /api/v1/auth/login` → 200, `ApiResult<LoginResponse>`
   - DTOs: `RegisterMerchantRequest` (validated with `@PhoneNumber` or `@Email`, `@SafeString`, `@Size`), `LoginRequest`, `LoginResponse`, `MerchantOnboardingDto`.

7. Per-module routes constants (`com.shadhinpay.identity.constant.IdentityRoutes`):
   - `public static final String AUTH_BASE = Routes.V1.BASE + "/auth";`
   - `MERCHANT_BASE`, `MERCHANT_REGISTER`, `AUTH_LOGIN`, etc.
   - Per cross-cutting decision #1, do NOT edit `common.constant.Routes`.

8. Mappers (`com.shadhinpay.identity.mapper`) — MapStruct, `componentModel = "spring"`. `UserMapper`, `MerchantProfileMapper`. Sensitive fields (`passwordHash`, `mfaSecret`, raw `kycData`) MUST NOT be in any DTO; verify via mapper test.

TESTS (target: 70% line coverage on this slice; full module hits 80% by 1c)

Unit:
- `EncryptedStringConverter` round-trip: encrypt→decrypt yields original; ciphertext is non-deterministic across two encrypts of the same input.
- `RegisterMerchantUseCaseImpl`: happy path; duplicate-identifier rejection; password is BCrypted (use `BCrypt.checkpw` to verify hash format).
- `AuthenticateUserUseCaseImpl`: happy path (PHONE), happy path (EMAIL), wrong password → UnauthorizedException, missing user → UnauthorizedException with the SAME message string (parameterized test), inactive user → UnauthorizedException.
- Mapper test: `MerchantOnboardingDto` from a populated `User+MerchantProfile` does NOT contain `passwordHash`, `mfaSecret`, or `kycData` (use Jackson serialization round-trip + JSON path assertion).

Integration (`@SpringBootTest` + Testcontainers Postgres):
- Polymorphic auth: register one merchant with phone (`01712345678`), one merchant with email (`x@y.com`); both can log in via `/auth/login` using the appropriate identifier. Verify regex routing.
- Soft-delete: register, soft-delete, register again with the same identifier — second call SUCCEEDS (the partial unique index allows reuse after soft delete).

Property (jqwik, ≥ 100 tries):
- BCrypt round-trip: any random password 8–72 bytes verifies against its own hash and not against any other random password from the same generator.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl shadhinpay-identity -am verify` BUILD SUCCESS.
- JaCoCo line coverage ≥ 70% on `shadhinpay-identity` (full 80% comes by end of 1c).
- `ModularityTests` and `ArchitectureRulesTest` green.
- `mvn spotless:check` clean.
- gitleaks clean.
- Commit message: `feat(identity): foundation + register/login (1a)`.

FORBIDDEN
- Touching events, MFA fields beyond declaring nullable columns, KYC submission logic. Those are 1b/1c.
- Editing `common.constant.Routes` (use IdentityRoutes per cross-cutting decision #1).
- Committing to `DOCS/contracts/openapi.json`.
- Distinguishing "user not found" from "wrong password" in any way (message, status, timing).
- Logging or returning `passwordHash`, `mfaSecret`, or raw `kycData`.
- Adding any root-pom dep.
- Hand-rolling BCrypt or HMAC — use the framework primitives.

Output: file tree of additions, JaCoCo summary tail, sample register + login JSON, list of `IdentityRoutes` constants added.
```

---

## Prompt 1b — identity KYC + admin lifecycle + events

```
You are continuing the `shadhinpay-identity` module on branch `phase-1/identity`. Prompt 1a is merged into this branch as a prior commit. Your sub-scope: KYC submission, admin verify/reject, user blocking, and the two Modulith event publications (`MerchantVerifiedEvent`, `UserBlockedEvent`).

READ FIRST
- The same docs as 1a, plus:
- shadhinpay-identity/src/main/java/com/shadhinpay/identity/events/MerchantVerifiedEvent.java + UserBlockedEvent.java (locked record shapes — read the constructors carefully)
- DOCS/features/identity/TECH_SPEC.md §3.2 (event semantics)
- The 1a commits on this branch (your prior code)

WORK ONLY IN — same scope as 1a.

DO NOT TOUCH
- The locked event records in `events/`.
- The 1a code unless you need to add a new field/method (e.g., extending `User.status` setter logic). If you modify 1a code, name the change clearly in the commit message.

DELIVERABLES

1. Use-case interfaces + impls (`usecase` + `usecase.impl`):
   - `SubmitKycDocumentsUseCase` — input `KycSubmissionRequest(nidFrontUrl, nidBackUrl, tradeLicenseUrl, tinUrl?)`. Encrypts the metadata into `MerchantProfile.kycData` (via the converter from 1a). Transitions `onboardingStatus REGISTERED → PENDING_VERIFICATION`. Reject from any other state with `InvalidOperationStateException`. Idempotent on re-submission ONLY if state is REGISTERED (re-submit while PENDING is rejected with a clear message).
   - `VerifyMerchantUseCase` (admin) — transitions `PENDING_VERIFICATION → VERIFIED`. Inside `@Transactional`, after the state save, publish `MerchantVerifiedEvent` via `ApplicationEventPublisher`. Reject from any other state.
   - `RejectMerchantUseCase` (admin) — `PENDING_VERIFICATION → REJECTED`. Mandatory `reason` (validate non-blank, max 500 chars, `@SafeString`). No event for now.
   - `BlockUserUseCase` (admin) — sets `User.status = BLOCKED`. Publishes `UserBlockedEvent`. Idempotent — calling on an already-BLOCKED user is a no-op (returns success, does NOT re-publish event).
   - `UnblockUserUseCase` (admin) — `BLOCKED → ACTIVE`. No event.

2. REST controllers:
   - Extend `MerchantAuthController` (or add a new `MerchantOnboardingController`):
     - `POST /api/v1/merchant/kyc` → 200, `ApiResult<MerchantOnboardingDto>`. Auth required (any merchant).
     - `GET /api/v1/merchant/me` → returns the caller's `MerchantOnboardingDto` (status visibility).
   - `AdminMerchantController`:
     - `GET /api/v1/admin/merchants` (paginated, filterable by `onboardingStatus`, `search` over fullName).
     - `POST /api/v1/admin/merchants/{id}/verify`.
     - `POST /api/v1/admin/merchants/{id}/reject` with `RejectMerchantRequest(reason)`.
     - `POST /api/v1/admin/users/{id}/block` with optional `BlockUserRequest(reason)`.
     - `POST /api/v1/admin/users/{id}/unblock`.
     - `@PreAuthorize("hasAuthority('ADMIN_MANAGER')")` on every method.
   - Add corresponding `IdentityRoutes` constants.

3. New DTOs: `KycSubmissionRequest`, `MerchantSummaryDto`, `RejectMerchantRequest`, `BlockUserRequest`.

4. Specs (`com.shadhinpay.identity.spec`): `MerchantSpec` for admin filtering (status, search, date range).

5. Event publication wiring:
   - Inside `VerifyMerchantUseCaseImpl.execute(...)`, AFTER the state save, before the method returns, call `eventPublisher.publishEvent(new MerchantVerifiedEvent(userId, merchantProfileId, Instant.now(), MDC.get("traceId")))`. The publish happens inside the `@Transactional` boundary so the event row lands in `event_publication` only on commit.
   - Same pattern for `BlockUserUseCaseImpl`.

TESTS (target: cumulative module coverage 75% after 1b)

Unit:
- All four new use cases: happy path + every state-transition rejection + idempotency where applicable.
- `BlockUserUseCase` on already-BLOCKED user does NOT call `eventPublisher.publishEvent` (verify with Mockito).

Integration (Testcontainers Postgres + Spring Modulith JDBC tables):
- KYC lifecycle: register → submit KYC → verify. Assert `event_publication` table has exactly one row after `verify` commits, payload deserializes back to `MerchantVerifiedEvent` with the right fields.
- BlockUser publishes `UserBlockedEvent` once; second BLOCK call publishes nothing.
- Reject with empty/blank reason → 400 with validation error envelope.

ACCEPTANCE CRITERIA (this sub-prompt)
- All from 1a still hold.
- Cumulative JaCoCo ≥ 75%.
- All four new use cases have at least one integration test against the real Modulith event store.
- Commit: `feat(identity): KYC + admin lifecycle + events (1b)`.

FORBIDDEN
- Modifying the locked event record shapes.
- Publishing events outside the `@Transactional` use-case boundary.
- Adding MFA logic (that's 1c).
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample event row from `event_publication` table, integration test results, cumulative JaCoCo tail.
```

---

## Prompt 1c — identity MFA + coverage push

```
You are completing the `shadhinpay-identity` module on branch `phase-1/identity`. Prompts 1a and 1b are merged into this branch. Your sub-scope: TOTP-based MFA (enable + verify) and the final coverage push to ≥ 80%.

This sub-prompt is the ONLY one in Wave A authorized to add a root-pom dependency, per cross-cutting decision #3. You will add `dev.samstevens.totp:totp:1.7.1` to root pom `<dependencyManagement>` and import it from `shadhinpay-identity/pom.xml`. No other root-pom edits permitted.

READ FIRST
- DOCS/features/identity/PRD.md §4.4 (MFA)
- DOCS/features/identity/TECH_SPEC.md §4.1 (auth context for MFA fits)
- The dev.samstevens.totp library README — https://github.com/samdjstevens/java-totp (use Context7 MCP to fetch docs if available; otherwise the README's RFC6238 examples are sufficient)
- The 1a + 1b commits on this branch
- Your current JaCoCo report (`mvn -pl shadhinpay-identity verify` then open `target/site/jacoco/index.html`)

WORK ONLY IN — same as 1a/1b, plus:
- Root `pom.xml` `<dependencyManagement>` (ONE entry: totp 1.7.1)
- `shadhinpay-identity/pom.xml` (ONE dep import: totp)

DO NOT TOUCH
- Any other root-pom section beyond the totp `<dependency>` entry.
- Any other module's pom.xml.

DELIVERABLES

1. Root pom: add `<dependency><groupId>dev.samstevens.totp</groupId><artifactId>totp</artifactId><version>1.7.1</version></dependency>` to `<dependencyManagement>`.

2. `shadhinpay-identity/pom.xml`: add the import (no version — managed at root).

3. Use-case interfaces + impls:
   - `EnableMfaUseCase` — input userId. Step 1 generate a 20-byte Base32-encoded secret (use `dev.samstevens.totp.secret.DefaultSecretGenerator`). Step 2 encrypt + store in `User.mfaSecret`. Step 3 build TOTP provisioning URI (`otpauth://totp/...`). Step 4 return `MfaEnableResponse(secret, provisioningUri, qrCodeBase64)` — the unencrypted secret is returned ONCE here; subsequent reads must NEVER expose it. Step 5 set a flag `User.mfaEnabled = true` (add this column via migration `V1005__identity_mfa_enabled.sql`).
   - `VerifyMfaUseCase` — input `(userId, code)`. Step 1 load + decrypt `mfaSecret`. Step 2 verify with `dev.samstevens.totp.code.DefaultCodeVerifier` (TimeProvider = `SystemTimeProvider`, allowedTimePeriodDiscrepancy = 1, i.e., ±30s skew). Step 3 throw `UnauthorizedException("Invalid MFA code")` on mismatch.
   - `DisableMfaUseCase` — clears `mfaSecret`, sets `mfaEnabled = false`. Requires the user's current password as additional auth (re-verify BCrypt before clearing). Reject if MFA already disabled.

4. Migration `V1005__identity_mfa_enabled.sql`: `ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT false;`.

5. REST controller (extend or add):
   - `POST /api/v1/auth/mfa/enable` → returns `MfaEnableResponse` (one-time secret + QR).
   - `POST /api/v1/auth/mfa/verify` → 200 OK on valid code, 401 on invalid.
   - `POST /api/v1/auth/mfa/disable` (requires password in body).
   - Add `IdentityRoutes` constants.

6. DTOs: `MfaEnableResponse(secret, provisioningUri, qrCodeBase64)`, `MfaVerifyRequest(code)`, `MfaDisableRequest(password)`.

7. **Coverage push:** run `mvn -pl shadhinpay-identity verify`, open the JaCoCo report, identify any class < 80% line coverage. Add unit tests until the module reports ≥ 80% line and ≥ 70% branch coverage. Prefer:
   - Tests against use-case error paths (state-transition rejections).
   - Tests against mapper edge cases (null inputs, optional fields).
   - Tests against repository custom queries (the `AndDeletedFalse` suffixes, partial-unique-index behavior).
   - Tests against controller-impl `@PreAuthorize` (use `@WithMockUser` + appropriate authorities).

TESTS

Unit:
- TOTP round-trip with a fixed seed and a fixed `Clock` — generated code verifies.
- TOTP fails on a code from a different secret.
- TOTP accepts a code from ±30s but rejects ±90s (use `Instant.now().minusSeconds(...)` injection).
- DisableMfa rejects on wrong password.

Integration (Testcontainers Postgres):
- Enable → store encrypted secret → verify with the unencrypted secret returned in the response → success. Re-enable on already-enabled user is rejected.
- Disable then Enable again → new secret, different from the prior one.

Property (jqwik, ≥ 100 tries):
- Identifier-detection round-trip (you may already have this from 1a — leave in place; if not, add it now).

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All from 1a + 1b still hold.
- JaCoCo ≥ 80% line, ≥ 70% branch on `shadhinpay-identity`.
- `mvn -pl shadhinpay-identity -am verify` BUILD SUCCESS.
- ArchUnit + ModularityTests green.
- `mvn -Pstatic-analysis verify` clean (PMD).
- gitleaks clean (the TOTP secret in tests must be a literal test fixture; if gitleaks complains, allowlist with a comment in `.gitleaks.toml`).
- Commit: `feat(identity): MFA + coverage push (1c) — closes Wave A identity`.

FORBIDDEN
- Adding any root-pom dep beyond the one listed (totp).
- Returning the MFA secret on any endpoint other than the one-time `/auth/mfa/enable` response.
- Logging the MFA secret, code, or any decrypted form.
- Distinguishing "MFA not enabled" from "Wrong code" in the verify response (timing or message).

Output: file tree, JaCoCo final tail (line + branch percentages per class), sample MFA enable + verify request/response, list of IdentityRoutes constants final state.
```
