# MFS Adapter Library — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/adapters/PRD.md
4. DOCS/features/adapters/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes no public REST endpoints; webhook receivers per vendor are in scope)

## Module scope
Defines the outbound `PaymentProvider` strategy port (`initiate`, `queryStatus`, `refund`, `supports`) and ships its concrete implementations: `MockAdapter` in Wave A; `BkashAdapter`, `NagadAdapter`, `StripeAdapter` (and others) in Wave C — one agent per real adapter, each strictly isolated. Hosts the shared `TokenService` for vendor session-token caching. Each adapter normalizes vendor responses into the unified `VendorResponse` record.

## Allowed dependencies
- conflux-common (read-only)
- Redis (via `common`'s cache abstraction) — required for `TokenService` token caching.
- No cross-module use-case interfaces are imported (Wave A — depends only on `common` + Redis).
- Publishes (events): none.
- Consumes (events): none.
- Exposes (port, used by other modules): `PaymentProvider` strategy interface (consumed by `payment-core`).

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
1. Every use case / adapter method listed in TECH_SPEC §3 is implemented and unit-tested.
2. JaCoCo line coverage ≥ 80% for this module.
3. Integration test that wires every shipped adapter through the `PaymentProvider` port and the `TokenService`.
4. Property tests (jqwik) for `ErrorMapper` exhaustiveness — every documented vendor code maps to a non-null `ErrorCode`.
5. **WireMock contract tests for every adapter**: success, vendor-error, vendor-timeout, 401-on-token-refresh-then-success.
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Each adapter gets its own isolated `OkHttpClient`** with the per-vendor timeout budget (5 s connect, 10 s read/write). A slow `BkashAdapter` must not be able to starve `NagadAdapter` — verify with the Wave C isolation test in TECH_SPEC §6.
- **`TokenService` caches via Redis** with a TTL matching each vendor's stated token validity. On 401, the adapter must refresh once and retry — but never loop. Mis-set TTLs cause silent reauth storms in production.
- **Credentials are passed in per call** as `VendorCredentials`; the adapter must never persist or log them. Treat them as PII-equivalent. The plaintext is supplied by `provisioning` after AES-256-GCM decryption — your job is to forward, not store.
- **Error mapping is exhaustive.** Each adapter owns an `ErrorMapper` that converts the vendor's native error code to the platform's `ErrorCode`. Unknown vendor codes map to `MFS_ADAPTER_FAILURE` (never to `INTERNAL_ERROR` — that's a platform bug, not a vendor problem).
- **`MockAdapter` is part of Wave A** and is the only adapter `payment-core` can rely on until Wave C ships. Its behavior is contract-driven (success/fail/cancel based on amount or metadata flag) — keep it deterministic and side-effect-free.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
