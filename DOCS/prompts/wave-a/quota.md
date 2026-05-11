# Phase 1 Wave A — `quota` module prompts

> **Branch:** `phase-1/quota` — run both sub-prompts sequentially in the same git worktree on the same branch.
> **Scope:** high-performance metering for `payment-core`. Per-merchant monthly count of PARTNER-mode transactions; soft-reservation flow (Reserve → Confirm/Release). Implements cross-module `Reserve/Confirm/Release/GetUsageUseCase`. **Fail-OPEN** on Redis outage.
> **Read first (every sub-prompt):** the [Wave A index](../PHASE_1_WAVE_A_PROMPTS.md) — cross-cutting decisions.

Sub-prompts:
1. [4a — Reserve/Confirm/Release + invariants](#prompt-4a--quota-reserveconfirmrelease--invariants)
2. [4b — controllers + jobs + coverage](#prompt-4b--quota-controllers--jobs--coverage)

---

## Prompt 4a — quota Reserve/Confirm/Release + invariants

```
You are starting the `conflux-quota` module on branch `phase-1/quota`. This is the FIRST of TWO sequential sub-prompts (4a → 4b).

Your sub-scope: Flyway, the single entity, the Redis cache port + Redis adapter, the four locked cross-module use cases (Reserve/Confirm/Release/GetUsage), the fail-OPEN behavior, and the property + concurrency invariant tests.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2, §10.1, §10.2
- DOCS/prompts/PHASE_1_WAVE_A_PROMPTS.md "Cross-cutting decisions" section
- conflux-quota/CLAUDE.md
- DOCS/features/quota/PRD.md (full)
- DOCS/features/quota/TECH_SPEC.md (full)
- conflux-quota/src/main/java/pay/conflux/backend/quota/usecase/ (locked: `ReserveQuotaUseCase`, `ConfirmQuotaUseCase`, `ReleaseQuotaUseCase`, `GetUsageUseCase`, `QuotaReservation`, `QuotaUsageView`)

WORK ONLY IN
- conflux-quota/src/main/java/pay/conflux/backend/quota/{entity,repository,usecase.impl,cache,config,constant}/...
- conflux-quota/src/test/...
- conflux-application/src/main/resources/db/migration/V1005__quota_schema.sql
- conflux-application/src/test/java/pay/conflux/backend/quota/...

DO NOT TOUCH
- conflux-quota/src/main/java/pay/conflux/backend/quota/usecase/ (locked)
- conflux-common/, any other module
- Root pom.xml (no deps needed)
- common.constant.Routes (use QuotaRoutes in 4b)
- DOCS/contracts/openapi.json

DELIVERABLES

1. Flyway `V1005__quota_schema.sql`:
   - `quota_usage` (id UUID PK, merchant_id UUID, period VARCHAR(7), partner_mode_count INTEGER DEFAULT 0, audit + version BIGINT DEFAULT 0). Unique `(merchant_id, period)`.

2. Entity (`pay.conflux.backend.quota.entity.QuotaUsage extends Auditable`) — `@Version` for write-behind from the monthly job (4b).

3. Repository — `QuotaUsageRepository.findByMerchantIdAndPeriod(UUID, String)`, paginated for admin (4b).

4. Cache port (`pay.conflux.backend.quota.cache.QuotaCachePort`) — interface:
   - `String reservePending(UUID merchantId, String period)` — creates a unique pending key and returns the reservationId. (Implementation: `SET pending:{merchantId}:{period}:{newUuid} 1 EX 1800 NX`.)
   - `boolean releasePending(UUID merchantId, String period, String reservationId)` — DEL the pending key. Returns true if a key was deleted (idempotent).
   - `boolean confirmReservation(UUID merchantId, String period, String reservationId)` — atomic: DEL pending key + INCR final key. If the pending key didn't exist, no-op (return false). Use a Lua script for atomicity.
   - `int getFinalCount(UUID merchantId, String period)` — GET final key, default 0.
   - `int countPending(UUID merchantId, String period)` — count of `pending:{merchantId}:{period}:*` keys (use `SCAN`, NOT `KEYS`).

5. `RedisQuotaCacheAdapter implements QuotaCachePort`:
   - Key format per TECH_SPEC §2.2: `quota:final:{merchantId}:{period}` and `quota:pending:{merchantId}:{period}:{reservationId}`.
   - TTL: pending = 30 minutes (the leaked-reservation safety net at the cache layer); final = 35 days (covers month-end + reconciliation window).
   - All operations atomic. `confirmReservation` uses a Lua script to do `DEL pending + INCR final` atomically.

6. Use-case implementations (`pay.conflux.backend.quota.usecase.impl`):
   - `ReserveQuotaUseCaseImpl`:
     1. Compute `period = YearMonth.now(ZoneOffset.UTC).toString()` (format `YYYY-MM`). Re-compute every call. NEVER cache.
     2. Call `cachePort.reservePending(merchantId, period)` → reservationId.
     3. Read `getFinalCount + countPending - 1` (subtract this reservation since we already counted it). If `<= 10` → status `FREE`; else `BILLABLE`.
     4. Return `QuotaReservation(reservationId, status)`.
     5. **Fail-OPEN**: catch `RedisException`/`DataAccessException`/`TimeoutException`/etc. — log ERROR with structured fields (merchantId, period, exception class) → return `QuotaReservation(UUID.randomUUID().toString(), FREE)`. Payment must keep flowing.
   - `ConfirmQuotaUseCaseImpl`:
     1. Period from current time.
     2. `cachePort.confirmReservation(merchantId, period, reservationId)`. If returns false (no pending) → log warn, return silently (idempotent — already confirmed/released/expired).
     3. Persist? NO — write-behind via the monthly job (4b). The Redis final counter is the source of truth in-month.
     4. Fail-OPEN.
   - `ReleaseQuotaUseCaseImpl`:
     1. Period from current time.
     2. `cachePort.releasePending(...)`. Idempotent.
     3. Fail-OPEN.
   - `GetUsageUseCaseImpl`:
     1. Period from current time (or from input if provided — read the locked interface).
     2. Read `getFinalCount` from Redis.
     3. Compose `QuotaUsageView(usedCount, freeRemaining=max(0, 10-usedCount), period)`.
     4. On Redis failure: read `quota_usage` row from DB as fallback; if no row, return zeros. Log warn. Slightly stale is acceptable for the dashboard.

7. `QuotaConfig` — `@Configuration` exposing:
   - `freeQuotaPerMonth = 10` from `application.yml` `conflux.quota.free-per-month`.
   - The Lua scripts as `RedisScript<...>` beans.

8. Per-module routes constants (`pay.conflux.backend.quota.constant.QuotaRoutes`) — empty placeholder; controllers come in 4b.

TESTS (target: 65% module coverage on this sub-prompt)

Unit:
- `RedisQuotaCacheAdapter` round-trip with Testcontainers Redis: reservePending → confirmReservation increments final + drops pending atomically.
- Fail-OPEN: inject a `QuotaCachePort` that throws → `Reserve` returns FREE, error log emitted (use `LogCaptor`).
- Period rollover: with a mocked `Clock` at 31 May 23:59:59 then 1 Jun 00:00:01, two distinct keys are touched.
- ConfirmQuotaUseCaseImpl on already-confirmed reservation is a no-op (no double-INCR).

Integration (Testcontainers Postgres + Redis):
- **Reserve/Confirm/Release sequence:** 15 reservations, confirm 8, release 7 → `getFinalCount == 8`, `countPending == 0`. (DB persistence happens in 4b.)
- **Concurrency:** `CountDownLatch` for 100 simultaneous `Reserve+Confirm` against the same merchant → `getFinalCount == 100`. (Verifies atomic INCR.)
- **Leaked reservation:** Reserve, then sleep past TTL (in test, set TTL to 1 second by overriding `application-test.yml` — or use `@DynamicPropertySource`); subsequent `confirmReservation` is a no-op (key already expired).

Property (jqwik, ≥ 200):
- **Reserve/Confirm/Release invariant:** for any random sequence of operations, `final_count + pending_count == issued − released`.
- **CUSTOM never meters:** for any randomized sequence where `Reserve` is never called, no Redis keys exist for that merchant/period and DB has no row.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-quota -am verify` BUILD SUCCESS.
- JaCoCo ≥ 65%.
- Concurrency test (100 threads → 100 final) green.
- Resiliency test green (Redis container shutdown mid-test → Reserve returns FREE + log warn, asserted via LogCaptor).
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(quota): Reserve/Confirm/Release + invariants (4a)`.

FORBIDDEN
- Fail-CLOSED behavior. Quota outage must NOT block payments.
- Caching the `period` string at startup or in a singleton field.
- Read-modify-write against Redis. `INCR`/`DECR`/Lua only.
- `KEYS *`. Use `SCAN`.
- Modifying the locked use-case interfaces or DTOs.
- Implementing controllers or background jobs (4b).
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample Redis key trace from a 3-reservation sequence (with TTL), jqwik tries summary, JaCoCo tail.
```

---

## Prompt 4b — quota controllers + jobs + coverage

```
You are completing the `conflux-quota` module on branch `phase-1/quota`. Prompt 4a is committed. Your sub-scope: REST controllers, the monthly persistence job, the leaked-reservation cleanup job, and the final coverage push.

READ FIRST
- DOCS/features/quota/PRD.md §3 (user stories drive the merchant endpoint)
- DOCS/features/quota/TECH_SPEC.md §4.2 (monthly persistence)
- The 4a commits

DELIVERABLES

1. REST controllers:
   - `MerchantQuotaController`: `GET /api/v1/merchant/usage` returns `QuotaUsageView` for the caller (scope from `SecurityUtils.currentMerchantId()`). NEVER accept merchantId as a parameter.
   - `AdminQuotaController`: `GET /api/v1/admin/quota?merchantId=&period=YYYY-MM` for support visibility.
   - `QuotaRoutes` constants.

2. Background jobs (`pay.conflux.backend.quota.job`):
   - `MonthlyPersistenceJob`:
     - `@Scheduled(cron = "0 5 0 1 * *")` (1st of month, 00:05 UTC).
     - For each `quota:final:*` Redis key matching the *previous* month, parse merchantId + period, read final count, upsert into `quota_usage` (insert-or-update via `ON CONFLICT (merchant_id, period) DO UPDATE`).
     - Use `SCAN` (NOT `KEYS`) to enumerate.
     - Idempotent: re-running the job returns the same result.
     - Leave Redis keys alone — TTL 35 days handles cleanup.
   - `LeakedReservationCleanupJob`:
     - `@Scheduled(fixedRate = 600000)` (every 10 min).
     - `SCAN` for `quota:pending:*` keys whose `TTL` is `>` 1700 seconds (≈ created less than 100s ago — these are healthy; skip them) AND `<` `0` (no TTL set, anomaly — DEL with WARN log).
     - Better approach: rely on the `EXPIRE` 30m TTL set in 4a; this job is belt-and-braces. Implementation: SCAN, for each pending key check `OBJECT IDLETIME` or `TTL`; DEL those with TTL absent or unexpectedly large + emit WARN.

3. DTOs: `QuotaUsageDto` (matches `QuotaUsageView` for the response), `AdminQuotaUsageDto` (admin variant with merchantId).

4. Mappers if needed.

5. Coverage push to ≥ 80%.

TESTS

Unit:
- `MonthlyPersistenceJob.run()` with mocked Redis returning known counts → upsert called with correct values.
- Job idempotency: run twice → same row state.

Integration (Testcontainers Postgres + Redis):
- Seed Redis with `quota:final:{m1}:2026-04` = 7, `{m2}:2026-04` = 12. Set system clock to 1 May 00:05. Run `MonthlyPersistenceJob.runOnce()`. Assert `quota_usage` rows for both merchants with matching counts.
- Re-run: rows unchanged.
- `MerchantQuotaController.getUsage` returns the caller's view; cannot read another merchant's usage.

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All from 4a still hold.
- JaCoCo ≥ 80% line / 70% branch on `conflux-quota`.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Commit: `feat(quota): controllers + jobs + coverage (4b) — closes Wave A quota`.

FORBIDDEN
- `KEYS *`. Use `SCAN`.
- Persisting on the hot path (Reserve/Confirm/Release). Write-behind only.
- Editing `common.constant.Routes` or `DOCS/contracts/openapi.json`.

Output: file tree, sample monthly job log + DB state, JaCoCo final tail.
```
