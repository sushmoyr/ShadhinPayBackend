# ConfluxPay — Autonomous AI-Agent Development Workflow

> **Purpose:** A repeatable workflow for building the ConfluxPay backend (and coordinating the frontend) using autonomous AI coding agents (Claude Code, Cursor agents, etc.). It treats each module's PRD + TECH_SPEC as the agent's contract and uses Spring Modulith's built-in verification as the safety net that keeps autonomous work honest.
>
> **Scope:** Backend only in detail. Frontend coordination is described in §9 but the frontend build itself is out of scope for this document.
>
> **Status:** Living document. Update after each phase retrospective.

---

## Table of Contents

1. [Why this workflow fits ConfluxPay](#1-why-this-workflow-fits-conflux)
2. [Core principles](#2-core-principles)
3. [Phase 0 — Foundation (sequential, single agent)](#3-phase-0--foundation-sequential-single-agent)
4. [Phase 1 — Parallel module waves](#4-phase-1--parallel-module-waves)
5. [Phase 2 — Integration & end-to-end](#5-phase-2--integration--end-to-end)
6. [Phase 3 — Hardening](#6-phase-3--hardening)
7. [Per-agent operating rules](#7-per-agent-operating-rules)
8. [CI gates & guardrails](#8-ci-gates--guardrails)
9. [Frontend coordination](#9-frontend-coordination)
10. [Domain-specific testing notes](#10-domain-specific-testing-notes)
11. [Tradeoffs & when to deviate](#11-tradeoffs--when-to-deviate)
12. [Quick checklist](#12-quick-checklist)

---

## 1. Why this workflow fits ConfluxPay

The codebase is *already* shaped for parallel agent work:

- **Hexagonal + UseCase architecture** — every module exposes ports (interfaces) and depends only on other modules' ports, never their entities or repositories. Agents can build against contracts.
- **Vertical slices per feature** — each module (`identity`, `provisioning`, `payment-core`, `ledger`, etc.) has its own PRD + TECH_SPEC; an agent can be briefed with a single self-contained pair.
- **Spring Modulith** — provides `ApplicationModules.verify()` which fails the build on any forbidden cross-module access. This is the guardrail that keeps an autonomous agent from taking shortcuts.
- **Explicit cross-module events** — `PaymentCompletedEvent`, `MerchantVerifiedEvent`, etc. are the only async surface area; once their shapes are locked, modules can be built independently.

The strategy is therefore: **lock contracts in Phase 0, then run agents in parallel waves against those contracts.**

---

## 2. Core principles

| Principle | Why it matters for autonomous agents |
|---|---|
| **Contract-first** | Agents need stable interfaces to build against in parallel. Event shapes and use-case interfaces are designed once, up front. |
| **Tests as the contract** | Agents pass example tests trivially. Invariant/property tests catch the bugs they actually produce. |
| **CI is the source of truth, not the agent's claim** | Never trust an agent's "done" message. Definition of done = green CI. |
| **Modulith verification + ArchUnit always run** | Architectural rules from `ARCHITECTURE.md` are mechanically enforced, not reviewed by humans. |
| **One agent per module, in a worktree** | Avoids merge conflicts and keeps each agent's context narrow (its own PRD + TECH_SPEC + `common`). |
| **No agent sees more than it needs** | Reduces hallucination surface and prevents agents from reaching into other modules' internals. |
| **Money domain → property-based tests** | Ledger and idempotency invariants must be tested with jqwik or equivalent, not hand-picked examples. |

---

## 3. Phase 0 — Foundation (sequential, single agent)

This phase is **non-negotiable** before any parallel module work begins. Mistakes here cause churn across every Phase 1 agent.

### 3.1 Scaffold the monolith
- Maven multi-module project.
- Spring Boot 3.x, Spring Data JPA, Spring Security, Spring Modulith.
- PostgreSQL + Redis (TestContainers in tests).
- MapStruct, Lombok, jqwik, ArchUnit, WireMock, Testcontainers.
- Java 21 baseline.

### 3.2 Build the `common` module
Per `DOCS/features/common/TECH_SPEC.md`:
- `ApiResult<T>`, `ApiResultMeta`, `PaginationInfo`, `ErrorCode` enum.
- `Auditable`, `AuditableAndSoftDeletable` base entities (use `@CreationTimestamp`/`@UpdateTimestamp` — never SQL triggers).
- `BaseException` + global `ExceptionTranslator` (`@ControllerAdvice`).
- `Routes` constants class.
- HMAC-SHA256 signing/verification utility for webhooks.
- MDC trace filter (`X-PGW-Trace-ID`) + propagation helpers.
- Hibernate tenant filter (`@FilterDef(name = "tenantFilter", ...)`) + `TenantInterceptor` enabling it from `SecurityContext`.
- `SecurityUtils` (`currentMerchantId()`, `currentBusinessId()`, `currentAdminId()`).
- AES-256-GCM encryption util for credential fields.
- `BigDecimal` helpers fixed at scale 4, `RoundingMode.HALF_EVEN`.

### 3.3 Lock cross-module contracts
This is the most important Phase 0 task. Once locked, agents in Phase 1 cannot change them without coordinated re-planning.

- **Spring Modulith events** (records, immutable):
  - `MerchantVerifiedEvent`
  - `UserBlockedEvent`
  - `PaymentInitiatedEvent`
  - `PaymentCompletedEvent` (carries `metadata` map; invoice listens for `invoice_id` here)
  - `PaymentFailedEvent`
  - `PaymentRefundedEvent`
- **Cross-feature use-case interfaces** (only the inbound ports other modules call):
  - `provisioning.GetBusinessByApiKeyUseCase`
  - `provisioning.GetVendorConfigUseCase`
  - `risk.EvaluateTransactionUseCase`
  - `quota.ReserveQuotaUseCase` / `ConfirmQuotaUseCase` / `ReleaseQuotaUseCase`
  - `ledger.RecordJournalEntryUseCase`
  - `ledger.GetAccountBalanceUseCase`
  - `payment-core.InitiatePaymentUseCase` (called by invoice)
- **`PaymentProvider` adapter port** — the strategy interface every MFS adapter implements (`initiate`, `queryStatus`, `refund`, `supports`).

### 3.4 Wire safety nets into CI
- `ApplicationModules.verify()` test in `application/` — fails build on forbidden cross-module access.
- ArchUnit suite encoding `ARCHITECTURE.md` §21 (no controller→repository, no entity→DTO, no DTO→entity, etc.).
- Spotless/Checkstyle for formatting.
- JaCoCo with **80% line coverage gate** per module.
- `springdoc-openapi` plugin emitting `openapi.json` on every build.

### 3.5 Lock the OpenAPI envelope
Generate the initial `openapi.json` from a stub controller. Commit it as a frozen reference so the frontend agent can start work in parallel against the schema (see §9).

### 3.6 Phase 0 deliverables checklist
- [ ] All Maven modules compile and pass empty `ApplicationModules.verify()`.
- [ ] `common` module is feature-complete and unit-tested ≥ 80%.
- [ ] All cross-module event records and use-case interfaces compile in their owning modules.
- [ ] CI runs Modulith verify + ArchUnit + JaCoCo gate on every PR.
- [ ] `openapi.json` is generated and committed.
- [ ] Per-module CLAUDE.md files are written (see §7.1).

---

## 4. Phase 1 — Parallel module waves

One agent per module. Each agent works in its own git worktree on a feature branch. Each agent's context is **only**:
- The module's PRD + TECH_SPEC
- `ARCHITECTURE.md`
- The module's CLAUDE.md (§7.1)
- The `common` module (read-only dependency)
- The cross-module contracts (read-only dependency)

Agents must **not** see other modules' implementations.

### 4.1 Wave A — Independent foundations (fully parallel)

| Module | Why it's in Wave A | Notes |
|---|---|---|
| `identity` | Depends only on `common` | Polymorphic auth (regex identifier detection), KYC/KYB lifecycle, RBAC, MFA |
| `ledger` | Depends only on `common` | Double-entry, sharded hot accounts, optimistic locking, idempotent journal entries |
| `risk` | Depends only on `common` + Redis | SpEL rule engine, blacklist, velocity counters |
| `quota` | Depends only on `common` + Redis | Soft-reservation flow, fail-open, monthly reset |
| `adapters` (skeleton + `MockAdapter` only) | Defines the port; real adapters land in Wave C | `BkashAdapter`/`NagadAdapter`/`StripeAdapter` are Wave C |

### 4.2 Wave B — Orchestration (parallel after Wave A merges)

| Module | Depends on | Notes |
|---|---|---|
| `provisioning` | `identity` use-case interfaces | `Business`, `VendorConfig`, `ApiKey` lifecycle; consumes `MerchantVerifiedEvent` |
| `payment-core` | `provisioning`, `risk`, `quota`, `adapters` (port + MockAdapter) | The orchestrator; publishes `PaymentCompletedEvent`/etc.; owns `WebhookOutbox` |

### 4.3 Wave C — Consumers & real adapters (parallel after Wave B merges)

| Module | Depends on | Notes |
|---|---|---|
| `invoice` | `payment-core.InitiatePaymentUseCase`, listens for `PaymentCompletedEvent` | Slug-based public pages, QR codes, expiry job |
| `settlement` | `ledger`, `payment-core` | CSV reconciliation, T+2 payouts, BEFTN files, VAT/AIT |
| `BkashAdapter`, `NagadAdapter`, `StripeAdapter` | Adapter port from Wave A | One agent per adapter, each isolated; uses `TokenService` for session tokens |

### 4.4 Wave coordination rules
- **A wave ships before the next wave starts.** No partial promotions; this prevents downstream agents from building against half-baked contracts.
- **Each wave ends with a merge train** of all its branches into `main`, with full CI green.
- **An orchestrator agent** runs the merge train and resolves trivial conflicts. Non-trivial conflicts kick back to the originating agent.

---

## 5. Phase 2 — Integration & end-to-end

Single orchestrator agent. No parallelism. Goal: prove the whole system works end-to-end with real Postgres, real Redis, and WireMock'd MFS vendors.

### 5.1 Required scenarios (Testcontainers + WireMock)
- **Happy path:** merchant signup → verify → create business → generate API key → initiate payment → vendor success callback → ledger entries → webhook delivered to merchant.
- **Idempotency:** 100 concurrent `POST /v1/payments` with the same `X-Idempotency-Key` → exactly one `Transaction`, one ledger entry.
- **Zombie recovery:** simulate vendor timeout → transaction lands in `PENDING_RECOVERY` → reconciliation poller resolves it → ledger consistent.
- **Webhook retry:** merchant endpoint returns 500 five times then 200 → exponential backoff schedule honored → final delivery succeeds.
- **Modulith event replay:** kill the `ledger` module mid-event → restart → `IncompleteEventPublications.resubmitIncompletePublications()` replays → ledger catches up; idempotency in `RecordJournalEntryUseCase` prevents double-posting.
- **Quota fail-open:** kill Redis → payments still succeed; quota metering logged for manual reconciliation.
- **Risk block:** transaction matching a hard-block rule → 403 → no MFS dispatch, no ledger entry.
- **Multi-tenancy isolation:** API key for Business A cannot read/write Business B's data; tenant filter is verified active.
- **Reconciliation:** upload mock bKash CSV with 1 amount mismatch + 1 missing-in-local + 1 missing-in-vendor → all 3 exception types created.
- **Invoice flow:** create invoice → public page loads → pay → `PaymentCompletedEvent` → invoice marked PAID → expired invoice blocks payment.

### 5.2 Trial-balance check
Periodic test that asserts `SUM(postings.amount) == 0` across the entire ledger after a randomized 1,000-transaction workload.

---

## 6. Phase 3 — Hardening

Multiple specialized agents in parallel.

| Agent | Scope |
|---|---|
| **security-reviewer** | Verify AES-256-GCM credential storage, BCrypt password hashing, HMAC signatures on every webhook, no plain-text credentials in any DTO/log/response, rate limiting on public endpoints, HTTPS enforced, HSTS headers |
| **performance** | Latency benchmarks: risk eval < 50ms, quota check < 10ms, API key validation < 50ms (cached). Stress test ledger sharding under 1k tx/sec |
| **resiliency** | Resilience4j circuit breakers per adapter; verify slow `BkashAdapter` does not block `NagadAdapter`; verify Redis outage triggers fail-open in quota only |
| **observability** | Verify trace ID propagates from inbound request → MDC → outbound MFS call → response header; metrics on all hot paths |
| **doc-updater** | Sync OpenAPI, generate per-module README from CLAUDE.md, freeze v1 API contract |

---

## 7. Per-agent operating rules

### 7.1 Per-module CLAUDE.md template
Every module gets its own CLAUDE.md at `feature/{name}/CLAUDE.md` containing:

```
# {Module Name} — Agent Brief

## Source of truth
- PRD: DOCS/features/{name}/PRD.md
- Tech Spec: DOCS/features/{name}/TECH_SPEC.md
- Architecture: ARCHITECTURE.md (must be obeyed)

## Allowed dependencies
- common (read-only)
- {list of cross-module use-case interfaces this module consumes}

## Forbidden
- Reaching into another feature's repository, entity, or implementation class
- Modifying common, contracts, or other features' code
- Skipping the global `ApiResult<T>` envelope
- SQL triggers for createdAt/updatedAt — use JpaAuditing
- Storing plain-text credentials anywhere

## Definition of done
- All use cases in this module's TECH_SPEC §3 implemented
- Unit tests ≥ 80% line coverage (JaCoCo)
- Property tests for {invariants specific to this module}
- WireMock contract tests for any external integrations
- ApplicationModules.verify() and ArchUnit suite green
- OpenAPI emitted; no breaking changes to common types
```

### 7.2 Definition of done (CI-enforced, not agent-claimed)
1. ✅ Module compiles on `main`'s baseline.
2. ✅ All declared use cases in the TECH_SPEC have at least one unit test and one integration test.
3. ✅ JaCoCo line coverage ≥ 80% for the module.
4. ✅ `ApplicationModules.verify()` passes.
5. ✅ ArchUnit suite passes.
6. ✅ Property tests for ledger invariants / idempotency where applicable.
7. ✅ No new direct cross-feature imports (mechanically enforced).
8. ✅ OpenAPI delta reviewed; no breaking changes to existing endpoints.
9. ✅ No secrets committed (gitleaks scan).

### 7.3 What an agent must always do
- Run the test suite before claiming done. Failing tests = not done.
- Use TDD for new use cases (RED → GREEN → IMPROVE).
- Follow `ARCHITECTURE.md` §22 naming conventions exactly.
- For any deviation from the spec, raise it in a comment on the PR — do not silently change behavior.

### 7.4 What an agent must never do
- Modify another module's code.
- Modify `common` or the cross-module contracts.
- Add `@Service` to a use-case class (use `@UseCase`).
- Bypass the `ApiResult<T>` envelope.
- Use `EnumType.ORDINAL` or `@Data` on JPA entities.
- Catch and swallow exceptions inside use cases.
- Commit secrets or skip security gates.

---

## 8. CI gates & guardrails

Every PR must pass, in order:

1. **Build** — `mvn -pl {changed-module} -am verify`
2. **Modulith verify** — `ApplicationModules.of(...).verify()`
3. **ArchUnit** — `ARCHITECTURE.md` rules
4. **Unit tests** — module + transitive
5. **Integration tests** — Testcontainers (Postgres + Redis)
6. **Coverage gate** — JaCoCo ≥ 80% on changed module
7. **OpenAPI diff** — no breaking changes without an explicit `breaking-change` label
8. **Secret scan** — gitleaks
9. **Static analysis** — SpotBugs + PMD on changed files

A failure in any of 1–9 blocks the merge. **Agents should be configured to retry on failure with the failure log in context, not bypass the gate.**

---

## 9. Frontend coordination

The frontend is built by a separate agent track. To avoid serializing on backend completion:

- **Backend emits `openapi.json`** on every successful build (via `springdoc-openapi`).
- **A reference `openapi.json` is committed** at the end of Phase 0 with stub controllers — this is enough for the frontend agent to generate types and mock data.
- **Frontend uses `openapi-typescript` (or `openapi-generator`)** to generate request/response types directly from that JSON.
- **The `ApiResult<T>` envelope is locked in Phase 0** so frontend doesn't churn when individual endpoints are added.
- **Frontend agent runs against a mock server** (Prism, MSW, or a generated Spring stub) until backend Wave B lands.
- **Breaking-change policy:** any backend PR that breaks the OpenAPI schema requires the `breaking-change` label and triggers a frontend agent to update its types in the same merge train.

This means: **frontend can start at end of Phase 0, in parallel with all of Phase 1.** No serialization.

---

## 10. Domain-specific testing notes

Money is involved. The default agent failure mode — "looks correct, passes happy-path tests" — is unacceptable here.

### 10.1 Use property-based testing (jqwik)
- **Ledger:** for any randomized sequence of postings, `SUM(postings.amount per journal) == 0` and `account.balance == SUM(postings)`.
- **Idempotency:** for any concurrent N requests sharing a key, exactly one transaction exists.
- **Quota:** for any randomized sequence of Reserve/Confirm/Release calls, `final_count + pending_count` equals issued reservations.
- **Reconciliation math:** `Gross == NetToMerchant + PlatformFee + VAT` for every transaction, every random fee schedule.

### 10.2 Concurrency tests (CountDownLatch)
- 100 simultaneous quota increments → counter exactly 100.
- 100 simultaneous balance updates on the same account → no lost updates (optimistic lock retry holds).
- Concurrent webhook deliveries → no duplicate sends.

### 10.3 Failure-injection tests
- Kill Redis mid-payment → quota fail-opens, payment still succeeds.
- Kill the JVM mid-event → on restart, Modulith replays incomplete publications, ledger catches up, no double-posting.
- Vendor returns 500 for token refresh → adapter retries, then circuit-breaker opens after threshold.

### 10.4 Security tests
- Encrypted `VendorConfig.credentials` are unreadable from a raw DB dump.
- Rotated API key returns 401 immediately, no grace period.
- Tenant A's API key cannot retrieve Tenant B's transactions even with a guessed UUID.
- Webhook signature mismatch → 401 logged, no processing.

---

## 11. Tradeoffs & when to deviate

### 11.1 The contract-first cost
Locking event shapes and use-case interfaces in Phase 0 is expensive. If `PaymentCompletedEvent` later needs a new field, multiple Wave A/B agents' work churns. Accept this cost — it pays for itself by Wave A because parallel agent work is the bottleneck-killer.

**Mitigation:** events should carry a generic `metadata` map (already in the spec) for fields that aren't load-bearing.

### 11.2 When to abandon parallelism
- If Phase 0 contract design takes more than ~1 week, you're discovering domain ambiguity. Don't paper over it with agents — resolve it with a human pass before launching Phase 1.
- If a single Wave A module fails Modulith verify three times in a row, that module's spec is probably under-specified. Pause and refine the TECH_SPEC before continuing.

### 11.3 When NOT to use this workflow
- If the team is < 2 humans, the orchestration overhead may exceed the parallelism benefit; prefer sequential module-by-module with a single agent.
- If the spec is still in flux, contract-first will churn. Stabilize the spec first.

### 11.4 Alternatives considered
- **Sequential by dependency order** (`common → identity → provisioning → ...`): safer, but serializes everything onto one agent. Rejected because the spec is mature and the modules are well-isolated.
- **Vertical slice per use case** (one agent per use case, threaded through all layers): hardest to coordinate, prone to architectural drift. Rejected.

---

## 12. Quick checklist

Use this as the day-to-day reference.

### Before Phase 1
- [ ] Maven multi-module scaffold compiles
- [ ] `common` module complete and ≥ 80% tested
- [ ] All cross-module event records exist and compile
- [ ] All cross-module use-case interfaces exist and compile
- [ ] `ApplicationModules.verify()` passes on empty modules
- [ ] ArchUnit suite enforces `ARCHITECTURE.md` §21
- [ ] CI runs all gates from §8
- [ ] `openapi.json` is generated and committed
- [ ] Per-module CLAUDE.md files written (§7.1)
- [ ] Frontend agent has the OpenAPI reference

### Per agent, per module
- [ ] Working in its own git worktree on a feature branch
- [ ] Briefed with PRD + TECH_SPEC + ARCHITECTURE.md + module CLAUDE.md only
- [ ] Has read-only access to `common` and contracts; no other feature code
- [ ] CI is configured to block merge until §7.2 passes
- [ ] Property tests written for any money/idempotency invariants

### Per wave
- [ ] All Wave N branches green on CI
- [ ] Merge train executed by orchestrator agent
- [ ] Modulith verify still green on `main` after merge
- [ ] OpenAPI emitted; frontend agent notified of any additions

### Phase 2
- [ ] All scenarios in §5.1 pass with real Testcontainers
- [ ] Trial balance test passes after 1,000-tx randomized workload

### Phase 3
- [ ] Security review agent green
- [ ] Performance NFRs met
- [ ] Observability traces verified end-to-end
- [ ] v1 API contract frozen

---

*Last updated: 2026-05-04. Revisit after Phase 0 completion and again after Wave A merges.*
