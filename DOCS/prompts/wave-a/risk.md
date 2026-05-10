# Phase 1 Wave A — `risk` module prompts

> **Branch:** `phase-1/risk` — run all three sub-prompts sequentially in the same git worktree on the same branch.
> **Scope:** synchronous decision service for `payment-core` (sub-50ms pre-flight scoring) + admin case-management. SpEL-driven rules, Redis blacklist + velocity counters. Implements cross-module `EvaluateTransactionUseCase`.
> **Read first (every sub-prompt):** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md) — cross-cutting decisions.

Sub-prompts:
1. [3a — persistence + admin CRUD](#prompt-3a--risk-persistence--admin-crud)
2. [3b — SpEL engine + caches (security-critical)](#prompt-3b--risk-spel-engine--caches-security-critical)
3. [3c — evaluation + benchmarks + coverage](#prompt-3c--risk-evaluation--benchmarks--coverage)

---

## Prompt 3a — risk persistence + admin CRUD

```
You are starting the `shadhinpay-risk` module on branch `phase-1/risk`. This is the FIRST of THREE sequential sub-prompts (3a → 3b → 3c).

Your sub-scope: Flyway schema, all four entities, repositories, and the admin CRUD use cases for rules / blacklist / merchant-risk-profiles. NO engine yet (3b), NO evaluation (3c). This sub-prompt produces a working admin surface for managing risk inputs.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md "Cross-cutting decisions" section
- shadhinpay-risk/CLAUDE.md
- DOCS/features/risk/PRD.md (full)
- DOCS/features/risk/TECH_SPEC.md §2 (entities)
- shadhinpay-risk/src/main/java/com/shadhinpay/risk/usecase/ (locked: `EvaluateTransactionUseCase`, `RiskDecision`, `TransactionContext`)

WORK ONLY IN
- shadhinpay-risk/src/main/java/com/shadhinpay/risk/{entity,repository,usecase.impl,controller,dto,mapper,spec,constant}/...
- shadhinpay-risk/src/test/...
- shadhinpay-application/src/main/resources/db/migration/V1004__risk_schema.sql
- shadhinpay-application/src/test/java/com/shadhinpay/risk/...

DO NOT TOUCH
- shadhinpay-risk/src/main/java/com/shadhinpay/risk/usecase/ (locked)
- shadhinpay-common/, any other module
- Root pom.xml (no deps in this sub-prompt)
- common.constant.Routes (use RiskRoutes)
- DOCS/contracts/openapi.json

DELIVERABLES

1. Flyway `V1004__risk_schema.sql`:
   - `risk_rules` (id UUID PK, name VARCHAR(255) UNIQUE, expression TEXT, score_weight INTEGER, action VARCHAR(8), is_active BOOLEAN DEFAULT true, audit + soft-delete).
   - `blacklist_entries` (id UUID PK, type VARCHAR(16), value VARCHAR(255), reason TEXT, expires_at TIMESTAMPTZ NULL, audit + soft-delete). Index `(type, value)` non-unique.
   - `merchant_risk_profiles` (merchant_id UUID PK, trust_level VARCHAR(16) DEFAULT 'NEW', custom_limits TEXT NULL, audit). No soft delete (record-per-merchant; updated in place).
   - `risk_evaluations` (id UUID PK, transaction_id UUID, merchant_id UUID, total_score INTEGER, decision VARCHAR(8), triggered_rule_ids TEXT, reason TEXT, evaluated_at TIMESTAMPTZ, audit). Indexes `(transaction_id)`, `(merchant_id, evaluated_at DESC)`. No soft delete (audit log).
   - Document storage choice for `triggered_rule_ids` (comma-separated UUIDs vs JSON array). Pick one and stick with it. Recommend JSON array via `JSONB` column for searchability.

2. Entities:
   - `RiskRule extends AuditableAndSoftDeletable`, `BlacklistEntry extends AuditableAndSoftDeletable`, `MerchantRiskProfile extends Auditable`, `RiskEvaluation extends Auditable` (append-only).
   - Enums: `RuleAction` (ALLOW/FLAG/BLOCK), `BlacklistType` (PHONE/EMAIL/IP/MERCHANT), `TrustLevel` (NEW/VERIFIED/TRUSTED/VIP).
   - `RiskEvaluation.triggeredRuleIds` — `List<UUID>` mapped via JPA `AttributeConverter` (List ↔ JSON string) or via Hibernate `@Type(JsonBinaryType.class)`. Pick one.

3. Repositories:
   - `RiskRuleRepository` — `findByIsActiveTrueAndDeletedFalse()`, paginated for admin.
   - `BlacklistEntryRepository` — `findByTypeAndValueAndDeletedFalseAndExpiresAtIsNullOrExpiresAtAfter(BlacklistType, String, Instant)` (or compose via Spec); also `findAllActiveByType(BlacklistType, Instant)` for cache hydration.
   - `MerchantRiskProfileRepository` — `findByMerchantId(UUID)`.
   - `RiskEvaluationRepository` — `findByTransactionId(UUID)`, paginated by `decision` for admin case-management.

4. Admin CRUD use cases (interface in `usecase.internal/`, impl in `usecase.impl/`):
   - Rules: `CreateRiskRuleUseCase`, `UpdateRiskRuleUseCase`, `DisableRiskRuleUseCase`, `ListRiskRulesUseCase` (paginated).
   - Blacklist: `AddBlacklistEntryUseCase`, `RemoveBlacklistEntryUseCase`, `ListBlacklistUseCase`.
   - Profiles: `UpsertMerchantRiskProfileUseCase`, `GetMerchantRiskProfileUseCase`.
   - On rule create / update / disable: leave a `// TODO 3b: invalidate CompiledRuleCache` comment. Cache wiring lands in 3b.
   - On blacklist add / remove: leave a `// TODO 3b: hot-update Redis SET` comment.

5. REST controller (`AdminRiskController`):
   - Rules: `GET/POST /api/v1/admin/risk/rules`, `PUT /api/v1/admin/risk/rules/{id}`, `DELETE /api/v1/admin/risk/rules/{id}` (soft).
   - Blacklist: `GET/POST /api/v1/admin/risk/blacklist`, `DELETE /api/v1/admin/risk/blacklist/{id}`.
   - Profiles: `PUT /api/v1/admin/risk/profiles/{merchantId}`, `GET /api/v1/admin/risk/profiles/{merchantId}`.
   - Cases endpoints (`/cases`) deferred to 3c.
   - `@PreAuthorize("hasAuthority('ADMIN_MANAGER')")`.
   - `RiskRoutes` constants.

6. DTOs, mappers, specs.

TESTS (target: 50% module coverage on this sub-prompt; 80% by 3c)

Unit:
- Each CRUD use case: happy + duplicate name (rule) / duplicate (type, value) (blacklist) / merchant not found (profile).
- Mapper tests.
- `BlacklistEntryRepository.findActive...`: rows with `expires_at` in the past are excluded.

Integration (Testcontainers Postgres):
- Create 5 rules, list them, disable one, re-list (4 active).
- Create blacklist entry; list returns it; expire (set `expires_at = NOW() - 1day`); list excludes it.
- Upsert merchant profile; second upsert updates in place (single row in table).

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl shadhinpay-risk -am verify` BUILD SUCCESS.
- JaCoCo ≥ 50%.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(risk): persistence + admin CRUD (3a)`.

FORBIDDEN
- Implementing `EvaluateTransactionUseCase` (3c).
- Adding the SpEL engine, blacklist cache, or velocity counters (3b).
- Implementing the `/cases` admin endpoints (3c).
- Editing the locked types or `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample admin POST/GET trace for rules + blacklist, JaCoCo tail.
```

---

## Prompt 3b — risk SpEL engine + caches (security-critical)

```
You are continuing the `shadhinpay-risk` module on branch `phase-1/risk`. Prompt 3a is committed.

This is the SECURITY-CRITICAL sub-prompt of Wave A. Your sub-scope: the hardened SpEL evaluator, the compiled-rule cache (Caffeine), the Redis-backed blacklist cache, the velocity counter, and the integration of these caches into 3a's admin CRUD flow (cache invalidation on rule update / blacklist add).

This sub-prompt is authorized to add Caffeine to root pom per cross-cutting decision #3.

READ THE FOLLOWING IN FULL — do not skim:
- DOCS/features/risk/TECH_SPEC.md §4 (every line).
- shadhinpay-risk/CLAUDE.md "SpEL is code execution" gotcha — fail-CLOSED, sanitize EvaluationContext, no T() / no method invocation surface.
- Spring SpEL reference, focusing on:
  - `StandardEvaluationContext` vs `SimpleEvaluationContext` (we'll start from `SimpleEvaluationContext` because it disables the dangerous features by default).
  - `SimpleEvaluationContext.Builder.forReadOnlyDataBinding().withRootObject(...)` pattern.
  - DataBindingPropertyAccessor (read-only field/property access).
  - Why we MUST NOT use `StandardEvaluationContext` here.
- (Use Context7 MCP to fetch Spring docs if available.)

WORK ONLY IN — same as 3a, plus root pom for Caffeine.

DELIVERABLES

1. Root pom: add `com.github.ben-manes.caffeine:caffeine` (let Spring Boot BOM pick the version) to `<dependencyManagement>`.

2. `shadhinpay-risk/pom.xml`: import caffeine.

3. `SafeSpelEvaluator` (`com.shadhinpay.risk.engine.SafeSpelEvaluator`):
   - Use `SimpleEvaluationContext` (NOT `StandardEvaluationContext`). Build with `.forReadOnlyDataBinding()`.
   - Restrict to `TransactionContext` getter access only — no method invocation, no constructor invocation, no type references, no bean references. `SimpleEvaluationContext` denies these by default; verify with adversarial tests below.
   - Compilation: parse expression once via `SpelExpressionParser`, store the compiled `Expression`. On parse failure → catch `ParseException` → mark the rule inactive in DB (call `DisableRiskRuleUseCase` from 3a) + log ERROR. Never crash the engine.
   - Evaluation: `Boolean evaluate(Expression compiled, TransactionContext ctx)`. On `EvaluationException` or any other RuntimeException at evaluation time → log WARN + return `false` (rule didn't match). Never propagate the exception (the higher-level eval flow's fail-closed is for engine failure, not single-rule failure).
   - Time-bound: enforce 50ms per-rule via `ExecutorService.submit(...).get(50, MILLISECONDS)`. On timeout → return `false` (no match). Cancel the future. Use a small dedicated `ExecutorService` configured in `RiskConfig`.
   - **Adversarial guards** (TESTS section below has the corresponding cases): the following inputs MUST NOT execute or escape the sandbox:
     - `T(java.lang.Runtime).getRuntime().exec("ls")`
     - `new java.io.File("/etc/passwd").exists()`
     - `@someBean.someMethod()`
     - `''.getClass().getClassLoader()`
     - `''.getClass().forName('java.lang.Runtime')`
   - Document: SimpleEvaluationContext denies type references and bean references at the parser level; property-accessor traversal (`getClass`, `class`) is denied by `DataBindingPropertyAccessor` because it only reflects on the root object's bean properties.

4. `CompiledRuleCache` (`com.shadhinpay.risk.engine.CompiledRuleCache`):
   - `@Component` wrapping a `Caffeine` cache `Cache<UUID, CompiledRule>` where `CompiledRule = (RiskRule, Expression)` value record.
   - `@PostConstruct loadAll()` — `RiskRuleRepository.findByIsActiveTrueAndDeletedFalse()` → parse each via `SafeSpelEvaluator`. Failures logged + DB marked inactive.
   - Public methods: `Collection<CompiledRule> snapshot()` (used by evaluation), `void invalidate(UUID ruleId)`, `void put(RiskRule, Expression)`.
   - Hot-reload: hook `CreateRiskRuleUseCaseImpl`, `UpdateRiskRuleUseCaseImpl`, `DisableRiskRuleUseCaseImpl` from 3a — after the DB transaction commits, call `CompiledRuleCache.invalidate(...)` then re-load if needed (do this inside `@TransactionalEventListener(AFTER_COMMIT)` on a small in-process event, or directly post-commit in the use case impl — pick one and document).

5. `BlacklistCache` (`com.shadhinpay.risk.engine.BlacklistCache`):
   - Redis-backed. One Redis Set per `BlacklistType`: `risk:blacklist:PHONE`, `risk:blacklist:EMAIL`, `risk:blacklist:IP`, `risk:blacklist:MERCHANT`.
   - `@PostConstruct hydrate()` — for each type, `findAllActiveByType` and `SADD` all values.
   - Public method: `boolean isBlacklisted(BlacklistType type, String value)` → `SISMEMBER`.
   - Hot-update: hook `AddBlacklistEntryUseCaseImpl` and `RemoveBlacklistEntryUseCaseImpl` to mirror to Redis post-commit.
   - Periodic re-hydration: `@Scheduled(fixedRate = 300000)` (5min) — defensive against drift if a hot-update path ever fails.

6. `VelocityCounter` (`com.shadhinpay.risk.engine.VelocityCounter`):
   - Redis `INCR` + `EXPIRE`. Key format: `risk:velocity:{merchantId}:{dimension}:{windowSeconds}` where `dimension ∈ {PER_MERCHANT, PER_IP, PER_PHONE}` and `windowSeconds = epochSecond / windowSize` (windowSize defaults: 60 for minute, 3600 for hour, 86400 for day).
   - Public method: `long incrementAndGet(UUID merchantId, VelocityDimension dim, long windowSize)` returns the post-increment value; sets EXPIRE = `2 * windowSize` (overlap so we don't lose counts at window edges).
   - Limits: read from `MerchantRiskProfile.customLimits` (JSON) or sensible defaults from `application.yml`.

7. `RiskConfig` (`com.shadhinpay.risk.config.RiskConfig`):
   - Defines the SpEL evaluator's `ExecutorService` bean (small, `Executors.newFixedThreadPool(8)`, daemon threads).
   - Defines `flagThreshold` from `application.yml` (`shadhinpay.risk.flag-threshold`, default 50).
   - Defines default velocity limits per profile.

NO new use cases in this sub-prompt — purely engine + cache layer. The integration with `EvaluateTransactionUseCaseImpl` happens in 3c.

TESTS (target: cumulative module coverage 70% after 3b)

Unit — adversarial SpEL (this is the load-bearing test):
- For each of the following expressions, assert that compilation either fails OR evaluation returns `false` (never throws to the caller, never executes the dangerous operation):
  1. `T(java.lang.Runtime).getRuntime().exec('ls')` — must NOT execute.
  2. `new java.io.File('/etc/passwd').exists()` — must NOT execute.
  3. `@someBean.someMethod()` — must NOT execute (no bean resolver).
  4. `''.getClass().getClassLoader()` — must NOT escape to the JVM classloader.
  5. `''.getClass().forName('java.lang.Runtime')` — must NOT resolve a type.
  6. `T(System).exit(0)` — must NOT execute.
  7. `transaction.metadata['key'].class.classLoader` — must NOT escape.
  8. A genuinely-valid expression like `transaction.amount.amount > 1000` — MUST evaluate correctly and return the right boolean.
- `SafeSpelEvaluator` time-bound: a malicious expression that loops forever (within the legal grammar — e.g., a deeply-nested arithmetic expression that takes >50ms to evaluate) returns `false` after the timeout fires.

Unit — caches:
- `CompiledRuleCache` `loadAll()`: 5 rules → 5 cached. Insert a syntactically invalid rule directly via repo → `loadAll()` after restart marks it inactive in DB and does not include it in the snapshot.
- `CompiledRuleCache.invalidate()` removes the entry; re-fetch from snapshot does not include it until re-loaded.
- `BlacklistCache.isBlacklisted` returns `true` for a value present in Redis SET, `false` otherwise.
- Hot-update: calling the admin `AddBlacklistEntryUseCase` results in `SISMEMBER` returning true on the next call.

Integration (Testcontainers Postgres + Redis):
- `BlacklistCache.hydrate()` populates Redis from DB on startup.
- `VelocityCounter` race: `CountDownLatch` for 1,000 concurrent `incrementAndGet` calls against the same merchant/dimension/window → final counter exactly 1,000 (atomic INCR).
- `CompiledRuleCache` re-loads after a rule update, picking up the new expression.

ACCEPTANCE CRITERIA (this sub-prompt)
- All from 3a still hold.
- Cumulative JaCoCo ≥ 70%.
- All 8 adversarial SpEL test cases green.
- Velocity counter race test green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(risk): SpEL engine + caches (3b) — SECURITY-CRITICAL`.

FORBIDDEN
- Using `StandardEvaluationContext`. `SimpleEvaluationContext` only.
- Adding any other root-pom dep beyond Caffeine.
- Marking adversarial tests as `@Disabled` or weakening their assertions to make them pass. If you cannot make a guard hold, STOP and document — do not ship a wide-open SpEL parser.
- Caching or logging blacklist values in plain text in any place other than the Redis SET (which IS the cache) — i.e., do not write a "recently blacklisted" log line that prints the value.
- `KEYS *` against Redis. Use `SCAN` if you need enumeration; prefer maintaining the type-keyed sets above.
- Implementing `EvaluateTransactionUseCase` (3c).

Output: file tree, full output of all 8 adversarial SpEL tests (one snippet per case showing the assertion + result), velocity-counter race test log, cumulative JaCoCo tail.
```

---

## Prompt 3c — risk evaluation + benchmarks + coverage

```
You are completing the `shadhinpay-risk` module on branch `phase-1/risk`. Prompts 3a + 3b are committed. Your sub-scope: the cross-module `EvaluateTransactionUseCaseImpl` pulling everything together, the case-management endpoints, the latency benchmark, and the final coverage push.

READ FIRST
- shadhinpay-risk/src/main/java/com/shadhinpay/risk/usecase/EvaluateTransactionUseCase.java + RiskDecision.java + TransactionContext.java (locked from Phase 0)
- DOCS/features/risk/TECH_SPEC.md §4.1 (the canonical algorithm) + §6 (latency: 20 rules in <10ms)
- DEVELOPMENT_WORKFLOW.md §10.4 (security tests)
- The 3a + 3b commits

DELIVERABLES

1. `EvaluateTransactionUseCaseImpl implements EvaluateTransactionUseCase`:
   - Wrap the entire body in a try/catch — any uncaught exception is mapped to `RiskDecision(BLOCK, 0, [], "RISK_ENGINE_FAILURE")` and logged ERROR. **Fail-CLOSED.**
   - Step 1 — Blacklist (cheapest first). For each non-null field of `TransactionContext` (customerPhone, customerEmail, ip, merchantId), call `BlacklistCache.isBlacklisted(...)`. On hit → `RiskDecision(BLOCK, 0, [], "Blacklist hit: " + type)`, persist `RiskEvaluation`, return.
   - Step 2 — Velocity. Increment counters per dimension (PER_MERCHANT minute/hour/day, PER_IP minute, PER_PHONE minute). If any exceeds the merchant's profile limit (or default), treat as a synthetic BLOCK rule.
   - Step 3 — Rule eval. Iterate `CompiledRuleCache.snapshot()`. For each match, sum `scoreWeight`. If a match has `action=BLOCK` → short-circuit return BLOCK with the rule id in `triggeredRuleIds`.
   - Step 4 — Decision. `BLOCK` (already returned), `FLAG` if `totalScore >= flagThreshold`, else `ALLOW`.
   - Step 5 — Always persist `RiskEvaluation` (even for ALLOW). This is the regulatory record.
   - Annotate `@UseCase` (the impl class), NOT `@Service`. Constructor injection only.

2. Case-management use cases (admin):
   - `ListPendingCasesUseCase` — paginated listing of `RiskEvaluation` rows where `decision == FLAG` and no admin action recorded yet.
   - `ApproveRiskCaseUseCase`, `RejectRiskCaseUseCase` — record the admin's decision against the `RiskEvaluation` row (add columns `reviewed_by_admin_id UUID NULL`, `review_decision VARCHAR(16) NULL`, `reviewed_at TIMESTAMPTZ NULL` via migration `V1006__risk_case_review.sql`).
   - These are internal; they don't unblock the original transaction (Wave B `payment-core` will integrate). Document this clearly.

3. Extend `AdminRiskController` with the case endpoints from 3a's TODO:
   - `GET /api/v1/admin/risk/cases?status=FLAGGED`
   - `POST /api/v1/admin/risk/cases/{evaluationId}/approve`
   - `POST /api/v1/admin/risk/cases/{evaluationId}/reject`

4. Latency benchmark (`shadhinpay-risk/src/test/java/com/shadhinpay/risk/benchmark/RiskLatencyBenchmark.java`):
   - Plain JUnit 5 (no JMH dep). Seed 20 rules with realistic SpEL expressions. Warm up: 1,000 evaluations. Measure: 10,000 evaluations of varied `TransactionContext` inputs. Compute p50, p95, p99 in microseconds.
   - Assert p99 < 10ms (TECH_SPEC §6 target). On a CI runner this may be flaky — if so, downgrade to a soft assertion + log+capture and document the actual numbers in the commit message. Hard failure threshold: p99 < 50ms.

5. Property tests (jqwik, ≥ 100):
   - Score monotonicity: adding a matching rule with positive scoreWeight to a context's evaluation can only increase or hold (never decrease) totalScore.
   - Decision precedence: if any matching rule has action=BLOCK, the final decision is BLOCK regardless of score.
   - Audit row exists: for any random TransactionContext, a `RiskEvaluation` row is persisted exactly once per `evaluate()` call.

6. Coverage push to ≥ 80% line / ≥ 70% branch.

TESTS

Unit:
- Decision matrix: build TransactionContext + rule set; assert ALLOW/FLAG/BLOCK paths.
- Threshold boundary: `totalScore == flagThreshold − 1` → ALLOW; `== flagThreshold` → FLAG.
- Fail-closed: inject a CompiledRuleCache that throws on `snapshot()` → decision is BLOCK with `RISK_ENGINE_FAILURE`.

Integration (Testcontainers Postgres + Redis):
- Seed 5 rules + 3 blacklist entries; evaluate 10 contexts; verify decisions and that all 10 `risk_evaluations` rows exist.
- Rule update invalidation: update a rule's expression via admin → next evaluation uses the new expression.
- Blacklist hot-load: insert via admin → next evaluation is a BLOCK on that value.
- Approve/Reject case round-trip: FLAG result → admin approves → `RiskEvaluation` row updated.

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All from 3a + 3b still hold.
- JaCoCo ≥ 80% line / 70% branch on `shadhinpay-risk`.
- Latency benchmark documented (commit message includes p50/p95/p99 numbers).
- All 8 adversarial SpEL guards from 3b still green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(risk): evaluate + cases + benchmark (3c) — closes Wave A risk`.

FORBIDDEN
- Fail-OPEN. Any uncaught exception → BLOCK.
- Modifying the locked `RiskDecision` / `TransactionContext` / `EvaluateTransactionUseCase`.
- Skipping the `RiskEvaluation` persistence on ALLOW decisions.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, latency benchmark numbers (p50/p95/p99), sample evaluation trace with all three decision types, JaCoCo final tail.
```
