# Business Provisioning & API Management — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/provisioning/PRD.md
4. DOCS/features/provisioning/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes business / vendor-config / API-key endpoints)

## Module scope
Owns the per-tenant configuration that every API request needs to resolve: `Business`, `VendorConfig` (encrypted MFS credentials, per-vendor mode), and `ApiKey` (hashed, prefixed, environment-scoped). Provides `payment-core` with the business + vendor credentials for every transaction; provides the global gateway filter with API-key validation. Listens for identity events to bootstrap a default business on merchant verification.

## Allowed dependencies
- shadhinpay-common (read-only)
- Cross-module use-case interfaces consumed: none directly — `identity` exposes its surface via events only in Phase 1.
- Redis (via `common`'s cache abstraction) — required for hot-path API-key and vendor-config lookups.
- Publishes (events): none in Phase 1.
- Consumes (events): `MerchantVerifiedEvent` (auto-creates the merchant's "Default Business"), `UserBlockedEvent` (cascades to API-key cache eviction).
- Exposes (use-case interfaces, called by other modules): `GetBusinessByApiKeyUseCase`, `GetVendorConfigUseCase`.

## Forbidden
- Reaching into another feature's `repository`, `entity`, or `mapper` packages.
- Modifying `shadhinpay-common`, the cross-module contracts, or any other feature module.
- Skipping the global `ApiResult<T>` envelope.
- SQL triggers for createdAt/updatedAt — use `@CreationTimestamp`/`@UpdateTimestamp`.
- Storing plaintext credentials, password hashes, or PII without encryption.
- Field injection (`@Autowired` on fields). Constructor injection only.
- `@Data` on JPA entities.
- `EnumType.ORDINAL`.

## Definition of done
1. Every use case listed in TECH_SPEC §3 is implemented and unit-tested.
2. JaCoCo line coverage ≥ 80% for this module.
3. Integration test for the consumed `MerchantVerifiedEvent` listener (default-business creation), including replay correctness.
4. Property tests (jqwik) for the API-key generation/validation invariant: a generated key always validates exactly once and produces the same `(businessId, environment)` tuple; the stored hash never collides for distinct plaintexts in a 100k-key sample.
5. WireMock contract tests for any external HTTP integration (none expected in Phase 1).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **`VendorConfig.credentials` is AES-256-GCM encrypted at rest** using a per-business key (Master Key + business salt). The mapper must *never* include the `credentials` field on any DTO returned to a controller — public exposure of a single decrypted blob is a P0 incident. Audit the mapper before merging.
- **API keys are hashed at rest, plaintext returned exactly once.** Generation: 32-char random → prefix (`sp_live_` / `sp_test_`) → store SHA-256 hash + prefix + last-4 → return full plaintext to the caller in the response and never again. A `GenerateApiKeyUseCase` that can return the plaintext on a subsequent call is broken.
- **`webhookUrl` must be HTTPS** at validation time; reject plain http on `UpdateWebhookUseCase`. The `webhookSecret` is encrypted at rest and used by `payment-core` for HMAC-SHA256 signing of outbound webhooks.
- **Cache invalidation:** Redis-cached `keyHash → {businessId, environment}` entries must be evicted on `UserBlockedEvent`, key rotation, or business deactivation. A rotated key returning 200 even once is a security regression — the rotation test in TECH_SPEC §6 must pass.
- **Tenant isolation:** every outbound surface must carry `(businessId, environment)`. Downstream modules will filter by these — but the source of truth is here.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
