# Risk & Fraud Engine — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/risk/PRD.md
4. DOCS/features/risk/TECH_SPEC.md
5. DOCS/contracts/openapi.json (this module exposes admin rule/case endpoints)

## Module scope
Synchronous decision service for `payment-core` (pre-flight transaction scoring) and an asynchronous case-management workflow for admins. Owns `RiskRule` (SpEL-driven), `BlacklistEntry`, `MerchantRiskProfile`, and `RiskEvaluation` audit logs. Tracks velocities in Redis.

## Allowed dependencies
- conflux-common (read-only)
- Redis (via `common`'s cache abstraction) — required for blacklist set, velocity counters, compiled-rule cache.
- No cross-module use-case interfaces are imported (Wave A — depends only on `common` + Redis).
- Publishes (events): none.
- Consumes (events): none in Phase 1.
- Exposes (use-case interfaces, called by other modules): `EvaluateTransactionUseCase`.

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
3. Integration test exercising the SpEL rule engine end-to-end against a live PostgreSQL + Redis Testcontainer.
4. Property tests (jqwik) for the **scoring invariants** below.
5. WireMock contract tests for any external HTTP integration (none expected).
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
- **Pre-flight latency budget < 50 ms** (TECH_SPEC §6 benchmark: 20 rules in under 10 ms). Pre-parse and cache SpEL `Expression` instances; never re-parse per request. Load active blacklists into a Redis Set for O(1) lookups.
- **Fail-CLOSED, not fail-open.** Risk is the gatekeeper for money movement. If the rule engine, blacklist cache, or velocity store throws, the decision must default to `BLOCK` (or surface the failure to the orchestrator) — *never* `ALLOW`. This is the opposite of `quota`'s fail-open posture; do not copy that pattern here.
- **Velocity counters** use Redis `INCR` + `EXPIRE` keyed `risk:velocity:{merchantId}:{type}:{window}`. Race-test with a `CountDownLatch` for 1,000 concurrent requests — the counter must equal exactly 1,000.
- **Decision audit:** every evaluation persists a `RiskEvaluation` row including `triggeredRuleIds`. Do not skip this for `ALLOW` decisions — it's the regulatory trail.
- **SpEL is code execution.** Rule expressions are admin-supplied — sanitize the EvaluationContext (no `T()` type references, no method invocation surface beyond `TransactionContext` getters). Any expression compilation failure must mark the rule inactive, not crash the engine.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
