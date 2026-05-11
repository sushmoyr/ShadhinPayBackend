# Identity & Merchant Onboarding — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/identity/PRD.md
4. DOCS/features/identity/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes auth/registration endpoints)

## Module scope
Centralized authority for authentication and identity management. Owns the `User` (polymorphic across MERCHANT/ADMIN, with regex-based identifier detection for PHONE/EMAIL/USERNAME), `MerchantProfile` (KYC/KYB lifecycle), and `AdminProfile`. Publishes lifecycle events that downstream modules (provisioning, risk) consume to bootstrap their own state.

## Allowed dependencies
- conflux-common (read-only)
- No cross-module use-case interfaces are imported (Wave A — depends only on `common`).
- Publishes (Spring Modulith events): `MerchantVerifiedEvent`, `UserBlockedEvent`.
- Consumes: none.

## Forbidden
- Reaching into another feature's `repository`, `entity`, or `mapper` packages.
- Modifying `conflux-common`, the cross-module contracts, or any other feature module.
- Skipping the global `ApiResult<T>` envelope.
- SQL triggers for createdAt/updatedAt — use `@CreationTimestamp`/`@UpdateTimestamp`.
- Storing plaintext credentials, password hashes, or PII without encryption.
- Field injection (`@Autowired` on fields). Constructor injection only.
- `@Data` on JPA entities.
- `EnumType.ORDINAL`.

## Definition of done
1. Every use case listed in TECH_SPEC §3 is implemented and unit-tested.
2. JaCoCo line coverage ≥ 80% for this module.
3. Integration test for every published Modulith event (`MerchantVerifiedEvent`, `UserBlockedEvent`).
4. Property tests (jqwik) for the regex-based `IdentifierDetector` (every valid BD phone resolves to `PHONE`; every `@`-containing string resolves to `EMAIL`; round-trip never misclassifies).
5. WireMock contract tests for any external HTTP integration (none currently expected).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- Polymorphic auth: a single login flow must handle PHONE / EMAIL / USERNAME. Detection is **regex-only** at the controller boundary (`^01[3-9]\d{8}$` for BD phone, `@` for email, fallthrough for username); never derive type from the user-supplied field.
- `BCrypt` for `passwordHash` (cost factor from common config; never hand-roll). `User.mfaSecret` and `MerchantProfile.kycData` must be encrypted at rest using the `common` AES-256-GCM utility — these fields must never appear in any response DTO or log line.
- Soft delete via `AuditableAndSoftDeletable`: every repository query must use the `AndDeletedFalse` suffix; a `findByIdentifier` that returns deleted users is a regression.
- Events are published only after the state transition commits (`@TransactionalEventListener(phase = AFTER_COMMIT)` on the consumer side; publish inside the use case's `@Transactional` boundary).

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
