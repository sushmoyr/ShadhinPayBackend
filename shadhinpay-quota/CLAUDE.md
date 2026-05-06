# Quota & Metering — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/quota/PRD.md
4. DOCS/features/quota/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes a usage-query endpoint for the merchant dashboard)

## Module scope
High-performance metering utility for `payment-core`. Tracks per-merchant monthly usage of PARTNER-mode transactions in Redis (with PostgreSQL as the persisted audit trail). Implements a soft-reservation flow (Reserve → Confirm/Release) so a failed payment does not consume billable quota.

## Allowed dependencies
- shadhinpay-common (read-only)
- Redis (via `common`'s cache abstraction) — required for atomic counters.
- No cross-module use-case interfaces are imported (Wave A — depends only on `common` + Redis).
- Publishes (events): none.
- Consumes (events): none.
- Exposes (use-case interfaces, called by other modules): `ReserveQuotaUseCase`, `ConfirmQuotaUseCase`, `ReleaseQuotaUseCase`.

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
3. Integration test for the monthly persistence/reset job and the leaked-reservation cleanup job (Testcontainers Redis + Postgres).
4. Property tests (jqwik) for the **reservation invariants** below.
5. WireMock contract tests for any external HTTP integration (none expected).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **PARTNER mode is metered; CUSTOM mode is skipped entirely.** The mode comes in on the `ReserveQuotaUseCase` request — do not infer it from any other module. A merchant on CUSTOM never touches Redis; never persist a `QuotaUsage` row for them.
- **Fail-OPEN on Redis outage.** If `INCR` throws or times out, return `FREE` and emit a structured error log for manual reconciliation. Payments must keep flowing. This is the opposite of `risk`'s fail-closed posture — if you find yourself catching the same exception in both modules, you are in the wrong one.
- **Reserve/Confirm/Release invariant:** for any sequence of Reserve/Confirm/Release calls, `final_count + pending_count == issued_reservations`. Property-test this with jqwik. A leaked `pending` reservation older than 30 minutes must be reclaimed by the cleanup job.
- **Month rollover:** the Redis key is `quota:{merchantId}:{period}` where `period = YYYY-MM` of the *current system time*. At 31 May 23:59:59 → 1 June 00:00:01, two distinct keys must be hit. Do not cache the period string at startup.
- **Concurrency:** `CountDownLatch` integration test for 100 simultaneous reservations against the same merchant must produce a counter of exactly 100. Atomic `INCR` only — never read-modify-write.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
