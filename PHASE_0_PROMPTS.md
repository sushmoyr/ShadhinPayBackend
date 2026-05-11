# ConfluxPay — Phase 0 Agent Prompts

> **How to use this file:** Each section below is a self-contained prompt you can copy-paste into a fresh Claude Code session. They are ordered; do not run them out of order. After each prompt completes, verify the listed acceptance criteria locally before launching the next one.
>
> **Why so many prompts:** Phase 0 deliverables are listed as one bullet each in `DEVELOPMENT_WORKFLOW.md`, but each one is a 1–3 hour focused task. Splitting them into separate agent runs keeps the agent's context narrow and the failure mode small.
>
> **General rule for every prompt:** the agent must read the cited source docs *before* writing any code. The acceptance criteria in each prompt are the only valid "done" signal — if the agent claims done without them passing, the work is rejected.

---

## Index

1. [Prompt 1 — Maven multi-module scaffold](#prompt-1--maven-multi-module-scaffold)
2. [Prompt 2 — `common`: API envelope, errors, auditing, routes](#prompt-2--common-api-envelope-errors-auditing-routes)
3. [Prompt 3 — `common`: money & validation primitives](#prompt-3--common-money--validation-primitives)
4. [Prompt 4 — `common`: security, encryption, HMAC, multi-tenancy](#prompt-4--common-security-encryption-hmac-multi-tenancy)
5. [Prompt 5 — `common`: observability (trace ID + MDC)](#prompt-5--common-observability-trace-id--mdc)
6. [Prompt 6 — Spring Modulith setup + `ApplicationModules.verify()`](#prompt-6--spring-modulith-setup--applicationmodulesverify)
7. [Prompt 7 — ArchUnit rules from `ARCHITECTURE.md`](#prompt-7--archunit-rules-from-architecturemd)
8. [Prompt 8 — Cross-module event contracts](#prompt-8--cross-module-event-contracts)
9. [Prompt 9 — Cross-module use-case interfaces](#prompt-9--cross-module-use-case-interfaces)
10. [Prompt 10 — `PaymentProvider` adapter port](#prompt-10--paymentprovider-adapter-port)
11. [Prompt 11 — CI pipeline + JaCoCo + OpenAPI emission](#prompt-11--ci-pipeline--jacoco--openapi-emission)
12. [Prompt 12 — Per-module `CLAUDE.md` briefs](#prompt-12--per-module-claudemd-briefs)
13. [Prompt 13 — Phase 0 acceptance gate](#prompt-13--phase-0-acceptance-gate)

---

## Prompt 1 — Maven multi-module scaffold

```
You are bootstrapping the ConfluxPay backend monorepo. This is the very first task; nothing exists yet beyond the docs.

READ FIRST (in order):
- ARCHITECTURE.md (full file)
- DEVELOPMENT_WORKFLOW.md §3 (Phase 0 — Foundation)
- DOCS/features/common/TECH_SPEC.md
- All DOCS/features/*/TECH_SPEC.md to understand which modules exist

GOAL
Create a Maven multi-module project. No business logic yet; just the skeleton.

DELIVERABLES
1. Root `pom.xml` with `<packaging>pom</packaging>`, Java 21, Spring Boot 3.x parent, and these modules declared:
   - conflux-common
   - conflux-identity
   - conflux-provisioning
   - conflux-payment-core
   - conflux-adapters
   - conflux-ledger
   - conflux-quota
   - conflux-risk
   - conflux-invoice
   - conflux-settlement
   - conflux-application      (the Spring Boot bootstrap module that depends on every feature module)

2. Each module has its own `pom.xml`. Feature modules depend on `conflux-common`. The `conflux-application` module depends on every feature module.

3. Standard dependencies pinned in the root `<dependencyManagement>`:
   - spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation, spring-boot-starter-actuator
   - spring-modulith-bom (latest GA), spring-modulith-starter-core, spring-modulith-starter-jdbc, spring-modulith-starter-test
   - postgresql driver, flyway-core
   - lombok, mapstruct + mapstruct-processor
   - jjwt (api/impl/jackson)
   - resilience4j-spring-boot3
   - okhttp
   - testcontainers (postgres, junit-jupiter), wiremock-jetty12, jqwik, archunit-junit5, rest-assured
   - springdoc-openapi-starter-webmvc-ui

4. `conflux-application/src/main/java/pay/conflux/backend/ConfluxPayApplication.java` — `@SpringBootApplication` bootstrap class with `@EnableJpaAuditing`.

5. `conflux-application/src/main/resources/application.yml` with profiles for `dev`, `staging`, `prod`. Use `${SPRING_PROFILE:dev}`. Include placeholder Postgres + Redis config (env-var driven, no hardcoded creds).

6. `.gitignore` (Maven, IntelliJ, VS Code, .env), `.editorconfig`, and a top-level `README.md` that just points to `ARCHITECTURE.md`, `DEVELOPMENT_WORKFLOW.md`, and `PHASE_0_PROMPTS.md`.

7. Spotless Maven plugin configured at the root with Google Java Format.

PACKAGE STRUCTURE
Use `pay.conflux.backend.{module}` as the base package per module (e.g., `pay.conflux.backend.common`, `pay.conflux.backend.identity`).

ACCEPTANCE CRITERIA (you MUST verify before claiming done)
- `mvn -q clean verify` succeeds with zero source files (only the bootstrap class).
- `mvn spring-boot:run -pl conflux-application` starts the application without errors (it will exit if no other config; that's fine — the test is that wiring resolves).
- Spotless `mvn spotless:check` passes.
- Every module pom resolves with no version warnings.

FORBIDDEN
- Do NOT add any business logic, entities, controllers, or use cases. This prompt is *only* the skeleton.
- Do NOT commit any secrets or hardcoded credentials.
- Do NOT use deprecated Spring Boot 2.x patterns.
- Do NOT skip Spotless.

When done, output: a tree of created files, the final `mvn verify` log tail, and a short note on any decisions you made (e.g., exact Spring Boot version chosen).
```

---

## Prompt 2 — `common`: API envelope, errors, auditing, routes

```
The ConfluxPay Maven scaffold exists. You are now filling in the `conflux-common` module's foundational types.

READ FIRST
- ARCHITECTURE.md §10 (API Response Envelope), §11 (Exception Hierarchy), §13 (Route Constants), §7 (Entities), §18 (Auditing)
- DOCS/features/common/TECH_SPEC.md (full file)

WORK ONLY IN
- conflux-common/

DELIVERABLES (place in `pay.conflux.backend.common.*` sub-packages)

1. `dto/ApiResult.java` — generic envelope record with fields `data`, `meta`, `pagination`. Static factories per ARCHITECTURE.md §10:
   - `ok(T data)`, `ok(Page<T> page)`, `created(T data)`, `ok()` (Void), `error(HttpStatus, String, ErrorCode)`, `validationError(Map<String,String>)`.
   - All factories return `ResponseEntity<ApiResult<T>>`.
2. `dto/ApiResultMeta.java` with `success`, `message`, `errorCode`, `timestamp` and `success()` / `failure()` static factories.
3. `dto/PaginationInfo.java` with `from(Page<?>)`.
4. `dto/PaginationRequest.java` with `toPageable()` honoring a `paginate=false` flag (returns `Pageable.unpaged()`).
5. `error/ErrorCode.java` enum with at minimum: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `RESOURCE_ALREADY_EXISTS`, `INVALID_OPERATION_STATE`, `INSUFFICIENT_FUNDS`, `IDEMPOTENCY_CONFLICT`, `MFS_ADAPTER_FAILURE`, `VENDOR_DOWN`, `QUOTA_EXCEEDED`, `RISK_REJECTED`, `TOKEN_EXPIRED`, `INTERNAL_ERROR`.
6. `error/ApiOperationException.java` — abstract `RuntimeException` with `errorCode` and `status` fields.
7. `error/ResourceNotFoundException`, `DuplicateResourceException`, `ValidationException`, `InvalidOperationStateException`, `UnauthorizedException`, `ForbiddenException` — all extending `ApiOperationException`.
8. `handler/GlobalExceptionHandler.java` — `@ControllerAdvice` extending `ResponseEntityExceptionHandler`. Handlers for: `ApiOperationException`, `ResourceNotFoundException`, `DataIntegrityViolationException`, `MethodArgumentNotValidException` (validation). Log warn for 4xx, error for 5xx. Use SLF4J.
9. `entity/Auditable.java` — `@MappedSuperclass` with `@CreationTimestamp` `createdAt` and `@UpdateTimestamp` `updatedAt`.
10. `entity/AuditableAndSoftDeletable.java` — extends `Auditable` with `boolean deleted = false` (default false in column definition).
11. `constant/Routes.java` — final class with `private` constructor, nested static `V1` class, and nested `Admin`/`Public` namespaces. Add `BASE = "/api/v1"`. Per-feature route classes will be added by feature agents later — leave a comment placeholder.
12. `annotation/UseCase.java` — `@Component` meta-annotation per ARCHITECTURE.md §20.

TESTS (≥ 80% coverage on this module)
- Unit test factories of `ApiResult` for all six paths (ok, ok+page, created, void, error, validationError).
- Unit test `GlobalExceptionHandler` for each handled exception type using `MockMvc` against a tiny throwaway controller defined inside the test class.
- Unit test that `Auditable.createdAt` and `updatedAt` are populated by Hibernate when persisting a trivial JPA entity (use H2 in-test or Testcontainers Postgres).

ACCEPTANCE CRITERIA
- `mvn -pl conflux-common -am verify` is green.
- JaCoCo line coverage ≥ 80% for `conflux-common`.
- No usage of `@Data` on any entity (will be enforced later by ArchUnit; do not introduce now).
- All response factories return JSON matching the shape in ARCHITECTURE.md §10.

FORBIDDEN
- Do NOT add business logic, security, encryption, HMAC, or trace-ID classes — those are separate prompts.
- Do NOT introduce dependencies on any feature module.
- Do NOT use `EnumType.ORDINAL` anywhere.

Output: tree of created files, JaCoCo report tail, sample JSON of `ApiResult.ok(...)` and `ApiResult.error(...)`.
```

---

## Prompt 3 — `common`: money & validation primitives

```
You are extending `conflux-common` with the money handling and shared validation primitives that every feature module relies on.

READ FIRST
- DOCS/features/common/TECH_SPEC.md §3.2 (Monetary Representation)
- DOCS/features/ledger/TECH_SPEC.md §2.1 (BigDecimal precision)
- ARCHITECTURE.md §12 (Validation)

WORK ONLY IN
- conflux-common/

DELIVERABLES (in `pay.conflux.backend.common.*`)

1. `money/Money.java` — value object record wrapping `BigDecimal amount` and `String currency`. Enforce scale 4 with `RoundingMode.HALF_EVEN` in the canonical constructor. Provide `add`, `subtract`, `multiply(BigDecimal)`, `negate`, `isPositive`, `isZero`, `isNegative`. Throw `IllegalArgumentException` on currency mismatch.
2. `money/MoneyConverter.java` — JPA `AttributeConverter<Money, BigDecimal>` so entities can persist `Money` directly when desired.
3. `money/Currencies.java` — final class with `BDT = "BDT"` constant; placeholder for additional currencies.
4. `validator/PhoneNumber` annotation + `PhoneNumberValidator` — Bangladesh format, regex `^(?:\+?88)?01[3-9]\d{8}$`. Null is valid (use `@NotNull` separately).
5. `validator/Email` annotation + `EmailValidator` — RFC-compliant, reuse Hibernate's where possible; null is valid.
6. `validator/SafeString` annotation + `SafeStringValidator` — rejects strings that contain control chars or HTML tags (`<` and `>`).
7. `util/IdentifierDetector.java` — given a string, returns an enum `IdentifierType` (`PHONE`, `EMAIL`, `USERNAME`) based on regex. Used by `identity` module later. Logic per `DOCS/features/identity/TECH_SPEC.md §4.1`.
8. `util/IdentifierType.java` — enum.

TESTS
- Property test (`jqwik`) on `Money`: for any random `BigDecimal` inputs, `add(a,b).subtract(b)` == `a`, scale always 4, rounding HALF_EVEN.
- Unit tests on `IdentifierDetector` covering: BD phone with/without `+88`, email with/without subdomain, username, garbage input, empty/null.
- Unit tests on every custom validator (valid + invalid + null cases).

ACCEPTANCE CRITERIA
- `mvn -pl conflux-common -am verify` green.
- Coverage ≥ 80%.
- jqwik test runs ≥ 100 random examples per property.

FORBIDDEN
- Do NOT introduce framework dependencies for `Money` beyond JPA `AttributeConverter`.
- Do NOT make `Money` mutable.
- Do NOT use `double` or `float` anywhere.

Output: file tree, jqwik run summary, JaCoCo tail.
```

---

## Prompt 4 — `common`: security, encryption, HMAC, multi-tenancy

```
You are extending `conflux-common` with security primitives shared by every feature module.

READ FIRST
- ARCHITECTURE.md §17 (Security & Authorization)
- DOCS/features/common/TECH_SPEC.md §7 (Security & Multi-Tenancy), §8 (Transport & Webhook Integrity)
- DOCS/features/provisioning/TECH_SPEC.md §4.1 (Credential Encryption)
- DOCS/features/payment-core/TECH_SPEC.md §4.3 (Webhook Signing)

WORK ONLY IN
- conflux-common/

DELIVERABLES (in `pay.conflux.backend.common.*`)

1. `crypto/AesGcmCipher.java`
   - AES-256-GCM with 96-bit IV per encryption, 128-bit auth tag.
   - Methods: `String encrypt(String plaintext, String purpose)` and `String decrypt(String ciphertext, String purpose)`.
   - Output format: Base64 of `IV || ciphertext || tag`.
   - Master key loaded from env `CONFLUX_MASTER_KEY` (32 bytes Base64). Fail fast at startup if missing in non-`dev` profiles.
   - Per-purpose key derivation via HKDF-SHA256(masterKey, info=purpose). The `purpose` string is the column-level domain (e.g., `vendor-credentials`, `mfa-secret`).
2. `crypto/HmacSigner.java`
   - `String sign(byte[] payload, String secret)` using HMAC-SHA256, hex-encoded.
   - `boolean verify(byte[] payload, String secret, String signature)` constant-time comparison.
3. `crypto/CryptoConfig.java` — `@Configuration` exposing `AesGcmCipher` and `HmacSigner` as beans. Validates the master key at startup (length, base64-decodable).
4. `security/SecurityUtils.java`
   - `Optional<UUID> currentUserId()`, `currentMerchantId()`, `currentBusinessId()`, `currentAdminId()`.
   - Reads from `SecurityContextHolder` and a custom `Authentication` principal. Define a small `AuthenticatedPrincipal` record in this same package with fields: `userId`, `userType` (`MERCHANT`/`ADMIN`), `merchantId` (nullable), `businessId` (nullable for non-API-key auth), `environment` (`TEST`/`LIVE`, nullable).
5. `tenancy/TenantFilterDef.java` — JPA `@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "businessId", type = UUID.class))`. Provide an example marker on a placeholder entity (commented) so feature agents know the pattern.
6. `tenancy/TenantInterceptor.java` — Spring component (`HandlerInterceptor` or AOP aspect) that, after authentication, enables the `tenantFilter` on the current Hibernate `Session` using `SecurityUtils.currentBusinessId()`. If business context is absent (e.g., admin endpoints), the filter is skipped.
7. `webhook/WebhookSigner.java` — convenience wrapper around `HmacSigner` that produces the `X-PGW-Signature` header value for a JSON payload + secret. Documented in Javadoc.
8. `transport/HttpsRedirectConfig.java` — Spring config that emits HSTS header on every response (`Strict-Transport-Security: max-age=31536000; includeSubDomains`).

TESTS
- `AesGcmCipher`: round-trip property test (random plaintexts, random purposes); verify ciphertext changes between two encryptions of the same plaintext (random IV).
- `HmacSigner`: known-vector test against an RFC HMAC-SHA256 fixture; verify constant-time comparison rejects altered signatures.
- `SecurityUtils`: tests for both populated and empty `SecurityContext` cases.
- `WebhookSigner`: produces a deterministic signature for a known payload + secret; signature differs if payload differs by 1 byte.

ACCEPTANCE CRITERIA
- All tests green; coverage ≥ 80% on the `crypto` and `webhook` packages.
- Application fails to start in `prod`/`staging` profile if `CONFLUX_MASTER_KEY` is missing or invalid (verified via a `@SpringBootTest` with profile override).
- No plaintext secrets ever logged (verified by a static check that no `log.*` call inside `crypto/` or `webhook/` references the secret/key parameter).

FORBIDDEN
- Do NOT roll your own crypto primitives; use `javax.crypto` only.
- Do NOT store the master key in `application.yml`.
- Do NOT implement a `decrypt`-then-log debug helper.

Output: file tree, test results, sample of an encrypted ciphertext (Base64) for a known plaintext to confirm format.
```

---

## Prompt 5 — `common`: observability (trace ID + MDC)

```
You are adding the global trace ID propagation system to `conflux-common`.

READ FIRST
- DOCS/features/common/TECH_SPEC.md §9 (Observability & Traceability)

WORK ONLY IN
- conflux-common/

DELIVERABLES (in `pay.conflux.backend.common.observability.*`)

1. `TraceIdFilter.java` — servlet `OncePerRequestFilter` that:
   - Reads `X-PGW-Trace-ID` from the request; if absent or invalid UUID, generates a new UUID.
   - Stores it in SLF4J MDC under key `traceId`.
   - Writes it back as `X-PGW-Trace-ID` on the response.
   - Removes the MDC key in a `finally` block.
2. `TraceIdContextInitializer.java` — utility for non-HTTP contexts (e.g., scheduled jobs, async event listeners) so they set/clear MDC manually. Provide `runWithTraceId(Runnable)` and `callWithTraceId(Callable)`.
3. `TraceIdPropagator.java` — for outbound HTTP clients (used by adapters later): a small interceptor that adds `X-PGW-Trace-ID: <current MDC value>` to outgoing requests. Provide both an OkHttp `Interceptor` and a `RestClient`/`WebClient`-compatible variant.
4. `LoggingConfig.java` — `@Configuration` that ensures the default Spring Boot console pattern includes `[%X{traceId}]`. Provide a `logback-spring.xml` if needed.
5. Update existing `GlobalExceptionHandler` (from Prompt 2) to log `traceId` from MDC alongside any error.

TESTS
- Filter test: request with no header → response has a UUID `X-PGW-Trace-ID`; MDC contains it during request; MDC is cleared after.
- Filter test: request with a valid UUID header → response echoes the same value.
- Filter test: request with a malformed `X-PGW-Trace-ID` (non-UUID) → server replaces with a fresh UUID; logs a warn.
- Async propagation test: enqueue a task to a `TaskExecutor`, verify the trace ID survives via `TraceIdPropagator`.
- OkHttp interceptor test (use `MockWebServer`): outbound call carries the current MDC trace ID.

ACCEPTANCE CRITERIA
- All tests green.
- Sample log output (captured by `LogCaptor` or similar) shows the trace ID prefix on every line during a request.

FORBIDDEN
- Do NOT use thread-local hacks outside MDC.
- Do NOT block requests if MDC setup fails — log and continue.

Output: file tree, log sample, test results.
```

---

## Prompt 6 — Spring Modulith setup + `ApplicationModules.verify()`

```
You are wiring Spring Modulith into the ConfluxPay scaffold and adding the verification test that becomes the architectural safety net for every future agent.

READ FIRST
- ARCHITECTURE.md §21 (Dependency Rules)
- DOCS/features/common/TECH_SPEC.md §5 (Inter-Module Event Delivery — Spring Modulith)
- DEVELOPMENT_WORKFLOW.md §3.4 (Wire safety nets into CI), §8 (CI gates)

WORK ONLY IN
- conflux-application/
- conflux-common/  (only to add the events package described below)

DELIVERABLES

1. `conflux-application/pom.xml` — add `spring-modulith-starter-core`, `spring-modulith-starter-jdbc`, `spring-modulith-starter-test`.

2. `conflux-application/src/main/resources/db/migration/V0001__modulith_event_publication.sql` — Flyway migration creating the `event_publication` table per Spring Modulith's JDBC schema (look up exact DDL for the chosen Modulith version; do not invent columns).

3. `conflux-application/src/test/java/pay/conflux/backend/architecture/ModularityTests.java`:
   - Class with two methods:
     - `verifyModules()` calls `ApplicationModules.of(ConfluxPayApplication.class).verify()`.
     - `documentModules()` calls `new Documenter(modules).writeDocumentation()` to emit C4-style PlantUML to `target/spring-modulith-docs`.
   - Both annotated `@Test`. The first MUST fail the build on any forbidden cross-module access.

4. Module declarations: each feature module gets a `package-info.java` at its root package (e.g., `pay.conflux.backend.identity`) annotated with `@org.springframework.modulith.ApplicationModule(displayName = "...")`. List explicit `allowedDependencies` per ARCHITECTURE.md and DEVELOPMENT_WORKFLOW.md §3.3:
   - `identity` → no dependencies on other features.
   - `provisioning` → `identity`.
   - `payment-core` → `provisioning`, `risk`, `quota`, `adapters`.
   - `adapters` → no feature deps.
   - `ledger` → no feature deps.
   - `quota` → no feature deps.
   - `risk` → no feature deps.
   - `invoice` → `payment-core`.
   - `settlement` → `payment-core`, `ledger`.
   - All modules implicitly depend on `common`.

5. Each feature module gets a public sub-package `events` (e.g., `pay.conflux.backend.identity.events`). Inside, create empty placeholder so the package exists. Actual event records will be added in Prompt 8.

6. `application.yml`:
   - `spring.modulith.events.jdbc.schema-initialization.enabled: false` (Flyway owns the schema).
   - `spring.modulith.events.republish-outstanding-events-on-restart: true`.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-application -am test -Dtest=ModularityTests` is green.
- `target/spring-modulith-docs/components.puml` (or equivalent) is generated and shows all 9 feature modules + common.
- The Flyway migration applies cleanly against a Testcontainers Postgres.

FORBIDDEN
- Do NOT relax module boundaries to make `verify()` pass — if a violation appears later, it must be fixed at the source.
- Do NOT use `spring-modulith-events-kafka` or any non-JDBC variant.

Output: file tree, `verify()` test log, generated module diagram path.
```

---

## Prompt 7 — ArchUnit rules from `ARCHITECTURE.md`

```
You are encoding ARCHITECTURE.md's dependency and naming rules as ArchUnit tests so they become CI gates.

READ FIRST
- ARCHITECTURE.md §21 (Dependency Rules) — these are the "must-not" rules
- ARCHITECTURE.md §22 (Naming Conventions Summary) — these become naming rules
- ARCHITECTURE.md §4 (Use Cases), §5 (Controllers), §7 (Entities)

WORK ONLY IN
- conflux-application/src/test/java/pay/conflux/backend/architecture/

DELIVERABLES

`ArchitectureRulesTest.java` containing the following @ArchTest declarations (use ArchUnit's `@AnalyzeClasses(packages = "pay.conflux.backend")`):

1. `controllers_must_be_in_controller_package` — classes annotated `@RestController` reside in `..controller..`.
2. `controllers_must_not_access_repositories_directly` — classes in `..controller..` may not depend on classes in `..repository..`.
3. `controllers_must_not_use_entities` — classes in `..controller..` may not depend on classes in `..entity..`.
4. `usecases_implementations_must_be_annotated_UseCase` — classes ending with `UseCaseImpl` are annotated with `@UseCase` (from `common.annotation`).
5. `usecases_must_not_be_annotated_Service` — classes implementing any `*UseCase` interface are NOT annotated with `@Service`.
6. `usecases_must_be_in_usecase_package` — classes ending with `UseCase` or `UseCaseImpl` reside in `..usecase..`.
7. `entities_must_extend_Auditable` — classes annotated `@Entity` are assignable to `Auditable` (from `common.entity`).
8. `entities_must_not_use_Data_annotation` — classes annotated `@Entity` are NOT annotated with `lombok.Data`.
9. `entities_must_not_be_referenced_by_dtos` — classes in `..dto..` may not depend on classes in `..entity..`.
10. `dtos_must_not_be_referenced_by_entities` — classes in `..entity..` may not depend on classes in `..dto..`.
11. `repositories_must_be_in_repository_package` — classes annotated `@Repository` reside in `..repository..`.
12. `feature_packages_must_not_depend_on_other_feature_internals` — for each feature `X`, classes in `pay.conflux.backend.X` may only depend on `pay.conflux.backend.common..`, `pay.conflux.backend.X..`, or `pay.conflux.backend.{otherFeature}.events..` and `pay.conflux.backend.{otherFeature}.usecase` (interfaces only — verified by name suffix check) — NOT into another feature's `entity`, `repository`, or `mapper`.
13. `no_field_injection` — no field annotated `@Autowired` on a non-test class. Constructor injection only (Lombok `@RequiredArgsConstructor` is fine).
14. `no_System_out_or_err` — no class in `pay.conflux.backend..` calls `System.out` or `System.err`. Tests excluded.
15. `enums_for_status_must_be_String_mapped` — fields annotated `@Enumerated` use `EnumType.STRING`.

For each rule, include a one-line comment explaining which ARCHITECTURE.md section it enforces.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-application -am test -Dtest=ArchitectureRulesTest` is green against the current scaffold.
- Add a deliberately-violating throwaway class in a test resource fixture, verify the relevant rule fails, then remove the fixture.

FORBIDDEN
- Do NOT use ArchUnit's "freeze" feature to ignore existing violations. Real fixes only.

Output: list of rules, sample failure output from a deliberately broken case (then fixed), final green test log.
```

---

## Prompt 8 — Cross-module event contracts

```
You are defining every Spring Modulith event that crosses a module boundary in ConfluxPay. Once these are committed, feature agents in Phase 1 cannot change them without coordinated re-planning.

READ FIRST
- DOCS/features/common/TECH_SPEC.md §5 (Inter-Module Event Delivery)
- DOCS/features/identity/TECH_SPEC.md §3.2 (Outbound Events)
- DOCS/features/payment-core/TECH_SPEC.md §3.2 (Outbound Ports)
- DOCS/features/payment-core/PRD.md §4.1 (Lifecycle states)
- DOCS/features/ledger/TECH_SPEC.md §3.3 (Event Consumers)
- DOCS/features/invoice/TECH_SPEC.md §3.2 (Event Listeners)

WORK
Each event lives in the publishing module's `events` sub-package (created in Prompt 6). All events are immutable Java records.

DELIVERABLES

1. `pay.conflux.backend.identity.events.MerchantVerifiedEvent`
   - Fields: `UUID userId`, `UUID merchantProfileId`, `Instant occurredAt`, `String traceId`.

2. `pay.conflux.backend.identity.events.UserBlockedEvent`
   - Fields: `UUID userId`, `String reason`, `Instant occurredAt`, `String traceId`.

3. `pay.conflux.backend.payment_core.events.PaymentInitiatedEvent`
   - Fields: `UUID transactionId`, `UUID merchantId`, `UUID businessId`, `Money amount`, `String vendor`, `String mode`, `String merchantOrderReference`, `Map<String,String> metadata`, `Instant occurredAt`, `String traceId`.

4. `pay.conflux.backend.payment_core.events.PaymentCompletedEvent`
   - Fields: same as PaymentInitiatedEvent plus `String vendorTransactionId`, and `Money platformFee`. Metadata is the carrier for `invoice_id` (used by invoice module).

5. `pay.conflux.backend.payment_core.events.PaymentFailedEvent`
   - Fields: `UUID transactionId`, `UUID merchantId`, `UUID businessId`, `String vendor`, `ErrorCode errorCode`, `String reason`, `Map<String,String> metadata`, `Instant occurredAt`, `String traceId`.

6. `pay.conflux.backend.payment_core.events.PaymentRefundedEvent`
   - Fields: `UUID transactionId`, `UUID originalTransactionId`, `Money amount`, `Map<String,String> metadata`, `Instant occurredAt`, `String traceId`.

RULES
- All records, all fields `final` (records enforce this), all fields non-null EXCEPT where noted (e.g., `metadata` defaults to empty map — provide a compact constructor that replaces null with `Map.of()`).
- Every event includes `traceId` (String) so consumers can keep MDC context across the listener boundary.
- Every event includes `occurredAt` (Instant).
- No enums beyond what's already in `common.error.ErrorCode`. Use `String` for `vendor` and `mode` for now to avoid premature coupling — Phase 1 may promote these to enums.
- No JPA, no Spring annotations on events. They are pure data.

DOCUMENTATION
Add a `events.md` file under each module's `src/main/java/pay/conflux/backend/{module}/events/` describing what each event signals, when it fires, and which modules currently consume it. Keep concise (one paragraph per event).

TESTS
- One unit test per event verifying:
  - Compact constructor rejects nulls on required fields.
  - Compact constructor replaces null metadata with empty map.
  - Equality semantics work as expected.
- A single Modulith-aware integration test that publishes one of each event from a stub use case and verifies it lands in the `event_publication` table (Testcontainers Postgres).

ACCEPTANCE CRITERIA
- `mvn -pl conflux-application -am test` green.
- ArchUnit rule (from Prompt 7) `feature_packages_must_not_depend_on_other_feature_internals` still green — events live in publicly-visible sub-packages.
- Documentation files exist and are non-empty.

FORBIDDEN
- Do NOT add fields beyond those listed.
- Do NOT serialize events to JSON inside the record (Modulith handles persistence).
- Do NOT make any event mutable.

Output: file tree, sample event construction snippet, test results.
```

---

## Prompt 9 — Cross-module use-case interfaces

```
You are declaring the inbound use-case interfaces that one feature module exposes to another. Implementations come in Phase 1; this prompt is interfaces only.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §3.3 (Lock cross-module contracts)
- DOCS/features/provisioning/TECH_SPEC.md §3.1 (Inbound Ports)
- DOCS/features/risk/TECH_SPEC.md §3.1
- DOCS/features/quota/TECH_SPEC.md §3.1
- DOCS/features/ledger/TECH_SPEC.md §3.1
- DOCS/features/payment-core/TECH_SPEC.md §3.1

WORK
Each interface lives in the *owning* module's `usecase` sub-package and is the only thing other modules import.

DELIVERABLES (interfaces + small request/response records used by them)

1. `pay.conflux.backend.provisioning.usecase`
   - `GetBusinessByApiKeyUseCase` → `BusinessContext execute(String apiKey)`.
   - `GetVendorConfigUseCase` → `VendorConfigDescriptor execute(UUID businessId, String vendor)`.
   - DTOs (records) `BusinessContext` (`UUID businessId`, `UUID merchantId`, `String environment`, `String webhookUrl`) and `VendorConfigDescriptor` (`String vendor`, `String mode`, `Map<String,String> credentialsRefs`). NOTE: `credentialsRefs` is opaque pointers/handles, never plaintext secrets.

2. `pay.conflux.backend.risk.usecase`
   - `EvaluateTransactionUseCase` → `RiskDecision execute(TransactionContext ctx)`.
   - DTOs: `TransactionContext` (merchant id, amount, vendor, customer phone, customer email, ip, metadata), `RiskDecision` (`enum Action {ALLOW, FLAG, BLOCK}`, `int score`, `List<UUID> triggeredRuleIds`, `String reason`).

3. `pay.conflux.backend.quota.usecase`
   - `ReserveQuotaUseCase` → `QuotaReservation execute(UUID merchantId)`.
   - `ConfirmQuotaUseCase` → `void execute(UUID merchantId, UUID reservationId)`.
   - `ReleaseQuotaUseCase` → `void execute(UUID merchantId, UUID reservationId)`.
   - `GetUsageUseCase` → `QuotaUsageView execute(UUID merchantId, String period)`.
   - DTOs: `QuotaReservation` (`UUID reservationId`, `enum Status {FREE, BILLABLE}`), `QuotaUsageView` (used count, free remaining, period).

4. `pay.conflux.backend.ledger.usecase`
   - `RecordJournalEntryUseCase` → `void execute(JournalEntryRequest request)` (idempotent on `(sourceType, sourceId)`).
   - `GetAccountBalanceUseCase` → `Money execute(UUID ownerId, String accountCode)`.
   - DTOs: `JournalEntryRequest` (`String sourceType`, `String sourceId`, `String description`, `List<PostingRequest> postings`, `Instant occurredAt`), `PostingRequest` (`UUID accountId`, `Money amount`, `enum Type {DEBIT, CREDIT}`).

5. `pay.conflux.backend.payment_core.usecase`
   - `InitiatePaymentUseCase` → `PaymentInitiationResult execute(InitiatePaymentRequest request)`. (Called by `invoice` and by the public REST controller.)
   - DTOs: `InitiatePaymentRequest` (`UUID businessId`, `Money amount`, `String vendor`, `String merchantOrderReference`, `String callbackUrl`, `String webhookUrl`, `Map<String,String> metadata`, `String idempotencyKey`), `PaymentInitiationResult` (`UUID transactionId`, `String redirectUrl`, `String status`).

RULES
- Interfaces only; no `@UseCase` annotation here (that goes on the implementation).
- DTOs are records, immutable, non-null fields validated in compact constructor.
- All DTOs and interfaces live in publicly-visible packages — they are the API surface.
- Other modules importing these may NOT also import the owning module's `entity`, `repository`, or `usecase.impl` packages.
- Add Javadoc on every interface explaining: who calls it, when, what guarantees.

TESTS
- Compile-time only — no runtime tests in this prompt. The implementations come in Phase 1.

ACCEPTANCE CRITERIA
- Project compiles.
- `ApplicationModules.verify()` still green.
- ArchUnit rule from Prompt 7 still green.
- Javadoc generation succeeds without warnings (`mvn javadoc:javadoc -pl conflux-application -am`).

FORBIDDEN
- Do NOT add concrete implementations.
- Do NOT export DTOs that contain plaintext credentials, password hashes, or PII without masking.

Output: file tree, Javadoc summary, list of every public type added.
```

---

## Prompt 10 — `PaymentProvider` adapter port

````
You are defining the strategy port that every MFS adapter (bKash, Nagad, Stripe, Mock) will implement. Implementations come in Phase 1 Wave A (MockAdapter) and Wave C (real adapters).

READ FIRST
- DOCS/features/adapters/TECH_SPEC.md (full file)
- DOCS/features/adapters/PRD.md §4.1 (PaymentProvider Contract)

WORK ONLY IN
- conflux-adapters/

DELIVERABLES (in `pay.conflux.backend.adapters.*`)

1. `port/PaymentProvider.java` — interface:
   ```java
   public interface PaymentProvider {
       VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds);
       VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds);
       VendorResponse refund(VendorRefundRequest request, VendorCredentials creds);
       boolean supports(Vendor vendor);
   }
   ```

2. `port/Vendor.java` — enum: `BKASH, NAGAD, ROCKET, UPAY, PATHAO, MCASH, STRIPE, MOCK`.

3. `port/VendorPaymentRequest.java` — record: `UUID transactionId`, `Money amount`, `String merchantOrderReference`, `String callbackUrl`, `Map<String,String> metadata`.

4. `port/VendorRefundRequest.java` — record: `UUID transactionId`, `String originalVendorTrxId`, `Money amount`, `String reason`.

5. `port/VendorCredentials.java` — record holding decrypted credentials (`Map<String,String> values`). NOTE: instances are short-lived; never persisted, never logged.

6. `port/VendorResponse.java` — record: `VendorStatus status`, `String vendorTrxId`, `String redirectUrl`, `String rawResponse`, `ErrorCode errorCode` (nullable on success).

7. `port/VendorStatus.java` — enum: `INITIATED, PENDING, COMPLETED, FAILED, CANCELLED, UNKNOWN`.

8. `support/PaymentProviderRegistry.java` — `@Component` that injects all `PaymentProvider` beans and exposes `PaymentProvider lookup(Vendor vendor)`. Throws `MfsAdapterException` if no provider supports the vendor.

9. `error/MfsAdapterException.java` — extends `ApiOperationException` with `ErrorCode.MFS_ADAPTER_FAILURE`.

10. `support/HttpClientFactory.java` — produces an isolated `OkHttpClient` per vendor (5s connect, 10s read/write, max 50 connections), wired with the `TraceIdPropagator` interceptor from Prompt 5. This is the only place in the adapters module that constructs HTTP clients.

11. `support/TokenService.java` — interface only (`String getToken(Vendor v, VendorCredentials creds)`). Implementation is deferred to Wave C; provide a `NoopTokenService` for now that throws `UnsupportedOperationException`.

RULES
- No real adapter implementations in this prompt.
- All records immutable.
- `VendorCredentials.toString()` MUST redact values (override toString to print key names only, never values). Add a unit test that verifies redaction.

TESTS
- Unit test on `PaymentProviderRegistry` with two stub providers (one supports `MOCK`, one supports `STRIPE`). Lookup returns correct one; lookup of unsupported vendor throws.
- Unit test on `VendorCredentials.toString()` confirming no values appear in the output.
- Unit test on `HttpClientFactory` returning distinct client instances per vendor.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-adapters -am verify` green.
- Coverage ≥ 80% on the port + support packages (acceptable to be lower if implementations are small).
- ArchUnit + Modulith verify still green.

FORBIDDEN
- Do NOT implement BkashAdapter / NagadAdapter / StripeAdapter — Wave C territory.
- Do NOT cache `VendorCredentials` instances anywhere.
- Do NOT use a global shared `OkHttpClient`.

Output: file tree, redaction test output sample, registry test output.
````

---

## Prompt 11 — CI pipeline + JaCoCo + OpenAPI emission

```
You are wiring the CI pipeline and code-quality gates that every Phase 1 PR will run against.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §8 (CI gates & guardrails)
- ARCHITECTURE.md §22 (Naming Conventions Summary) — for OpenAPI tag naming

DELIVERABLES

1. `.github/workflows/ci.yml` (or GitLab/Bitbucket equivalent if you spot one in the repo — otherwise default to GitHub Actions):
   - Triggers: push to any branch, pull_request to `main`.
   - Java 21, Maven, cached `~/.m2`.
   - Steps in order: `spotless:check` → `verify` (runs unit + integration + Modulith verify + ArchUnit) → JaCoCo coverage gate → `springdoc-openapi` JSON emission → gitleaks → SpotBugs → PMD.
   - Fails the job if JaCoCo coverage is below 80% on any changed module (use `jacoco-maven-plugin` `check` goal with per-module thresholds).
   - Uploads `target/spring-modulith-docs/` and `target/openapi/openapi.json` as build artifacts.

2. Root `pom.xml` plugin additions:
   - `jacoco-maven-plugin` configured with `report` + `check` goals; `BUNDLE` line coverage minimum 0.80, branch coverage minimum 0.70.
   - `springdoc-openapi-maven-plugin` (or equivalent runtime dump): runs against the booted application during `integration-test` phase and writes `openapi.json` to `target/openapi/`.
   - `spotbugs-maven-plugin` and `maven-pmd-plugin` configured with conservative rule sets.
   - Gitleaks invoked via the GitHub action — no Maven plugin required.

3. `conflux-application/src/main/java/pay/conflux/backend/application/config/OpenApiConfig.java`:
   - Defines `@Bean OpenAPI` with title `"ConfluxPay API"`, version `"v1"`, contact, license.
   - Adds security scheme `"ApiKeyAuth"` for `Authorization` header with prefix `Bearer ` (placeholder for now; details refined in Phase 1).

4. Commit a *frozen reference* `openapi.json` at `DOCS/contracts/openapi.json`:
   - Run the build once locally.
   - Copy the emitted JSON into `DOCS/contracts/`.
   - Document at top of file: this is the Phase 0 frozen reference for the frontend agent.

5. `DOCS/contracts/README.md` — short note explaining that the frontend uses this file as its source of truth until Phase 1 Wave B lands; any backend change that breaks the schema requires the `breaking-change` PR label.

ACCEPTANCE CRITERIA
- CI pipeline runs end-to-end on a sample PR.
- Coverage gate triggers correctly (verify by submitting a deliberately undertested change to a feature branch — then revert).
- `DOCS/contracts/openapi.json` is committed and non-empty.
- Build artifacts include the Modulith C4 diagrams.

FORBIDDEN
- Do NOT lower the coverage thresholds to make a build green.
- Do NOT skip the gitleaks step.
- Do NOT include any actual secrets in `application.yml` for CI; use GitHub Actions secrets.

Output: CI run URL or local `act` log, OpenAPI file size + endpoint count, JaCoCo summary.
```

---

## Prompt 12 — Per-module `CLAUDE.md` briefs

````
You are writing the agent brief that every Phase 1 module agent will read at the start of its session. Each brief locks that agent to its module's PRD + TECH_SPEC and forbids cross-module access.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §7.1 (Per-module CLAUDE.md template), §7.2 (Definition of done), §7.3, §7.4
- All DOCS/features/*/PRD.md and TECH_SPEC.md (you need the dependency relationships)
- ARCHITECTURE.md (full file)

DELIVERABLES

For each of the 9 feature modules, create `conflux-{module}/CLAUDE.md` using the template below. Customize the "Allowed dependencies" and "Module-specific gotchas" sections per the module's TECH_SPEC.

TEMPLATE
```
# {Module Display Name} — Phase 1 Agent Brief

## Source of truth (read in order, before writing code)
1. ARCHITECTURE.md (project root)
2. DEVELOPMENT_WORKFLOW.md §7.2 (definition of done)
3. DOCS/features/{module}/PRD.md
4. DOCS/features/{module}/TECH_SPEC.md
5. DOCS/contracts/openapi.json (if this module exposes endpoints)

## Module scope
{One paragraph summarizing the module's job from its PRD §1.}

## Allowed dependencies
- conflux-common (read-only)
- {list of cross-module use-case interfaces this module is allowed to import — copy from DEVELOPMENT_WORKFLOW.md §3.3}
- {list of cross-module events this module publishes or consumes}

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
3. Integration test for every published/consumed Modulith event.
4. Property tests (jqwik) for {module-specific invariants — see below}.
5. WireMock contract tests for any external HTTP integration.
6. `ApplicationModules.verify()` and ArchUnit suite green.
7. OpenAPI delta reviewed; no breaking changes to existing endpoints.
8. No secrets committed (gitleaks scan).

## Module-specific gotchas
{Customize per module — examples:}
- ledger: every journal entry sums to zero; account balance equals SUM(postings); idempotent on (sourceType, sourceId).
- quota: PARTNER mode only is metered; CUSTOM mode skips entirely; fail-open on Redis outage.
- payment-core: idempotency on (merchantId, X-Idempotency-Key) for 24h; webhook signing with HMAC-SHA256; PENDING_RECOVERY is a real state.
- risk: pre-flight latency budget < 50ms; fail-open is NOT acceptable here — fail-closed (BLOCK).
- adapters: each adapter gets an isolated OkHttpClient; tokens cached via TokenService with TTL matching vendor.
- identity: regex-based identifier detection (PHONE/EMAIL/USERNAME); BCrypt for passwords; encrypt mfaSecret.
- provisioning: AES-256-GCM for VendorConfig.credentials; API keys hashed at rest, plaintext returned once.
- invoice: slug must be cryptographically unguessable; expiry job runs hourly.
- settlement: zero-tolerance for rounding errors; every action linked to a JournalEntry.

## What to do if the spec is ambiguous
Stop. Open a PR draft documenting the ambiguity. Do NOT make a unilateral decision on:
- Schema changes that require Flyway migrations beyond your module
- New cross-module events or use-case interfaces
- Changes to the `ApiResult<T>` envelope or `ErrorCode` enum
- Encryption / authentication / authorization patterns

For everything else, prefer the option that minimizes coupling.
```

ACCEPTANCE CRITERIA
- 9 `CLAUDE.md` files exist, one per feature module.
- Each lists the correct dependencies (cross-check against DEVELOPMENT_WORKFLOW.md §3.3 and §4.1–4.3).
- Each lists at least 2 module-specific gotchas drawn from the TECH_SPEC NFRs.
- A throwaway test agent given any one CLAUDE.md as its sole context can correctly identify which other modules it may import.

FORBIDDEN
- Do NOT include implementation details — these are briefs, not specs.
- Do NOT duplicate ARCHITECTURE.md content; reference it.

Output: 9 file paths, sample of one CLAUDE.md (e.g., the ledger one), confirmation that dependencies match DEVELOPMENT_WORKFLOW.md.
````

---

## Prompt 13 — Phase 0 acceptance gate

```
You are the orchestrator agent for Phase 0 acceptance. Your job is to verify that every Phase 0 deliverable from DEVELOPMENT_WORKFLOW.md §3.6 is actually in place. You write no production code; you produce a report.

READ FIRST
- DEVELOPMENT_WORKFLOW.md §3.6 (Phase 0 deliverables checklist), §12 (Quick checklist — Before Phase 1)
- All twelve prompt files above (1–12)

CHECKS TO RUN

For each item below, run the listed command and record PASS/FAIL with evidence (command output snippet).

1. **Maven scaffold compiles**
   - `mvn -q clean verify`
   - PASS criterion: exit 0.

2. **Common module is feature-complete**
   - Verify these classes exist (use `find` or `git ls-files`):
     - `pay.conflux.backend.common.dto.ApiResult`
     - `pay.conflux.backend.common.error.ErrorCode`, `ApiOperationException` and 6+ subclasses
     - `pay.conflux.backend.common.entity.Auditable`, `AuditableAndSoftDeletable`
     - `pay.conflux.backend.common.handler.GlobalExceptionHandler`
     - `pay.conflux.backend.common.constant.Routes`
     - `pay.conflux.backend.common.annotation.UseCase`
     - `pay.conflux.backend.common.money.Money`, `MoneyConverter`
     - `pay.conflux.backend.common.crypto.AesGcmCipher`, `HmacSigner`
     - `pay.conflux.backend.common.security.SecurityUtils`, `AuthenticatedPrincipal`
     - `pay.conflux.backend.common.tenancy.TenantInterceptor`
     - `pay.conflux.backend.common.observability.TraceIdFilter`, `TraceIdPropagator`
     - `pay.conflux.backend.common.webhook.WebhookSigner`
     - All custom validators (PhoneNumber, Email, SafeString)
   - PASS criterion: every file present.

3. **Common coverage ≥ 80%**
   - `mvn -pl conflux-common -am test`
   - Inspect `target/site/jacoco/index.html`.

4. **All cross-module event records exist and compile**
   - Verify presence of each event from Prompt 8 (5 records minimum).
   - `mvn -q compile`.

5. **All cross-module use-case interfaces compile**
   - Verify presence of each interface from Prompt 9.

6. **`PaymentProvider` port + registry exist**
   - Verify Prompt 10 deliverables.

7. **Modulith verify passes on the empty modules**
   - `mvn -pl conflux-application -am test -Dtest=ModularityTests`
   - PASS criterion: green.

8. **ArchUnit suite is green**
   - `mvn -pl conflux-application -am test -Dtest=ArchitectureRulesTest`

9. **CI pipeline runs green on a sample PR**
   - Either link to a successful CI run OR run `act` locally (`act pull_request`) and capture log.

10. **`DOCS/contracts/openapi.json` is committed and non-empty**
    - File size > 1 KB (Spring Boot ships some default endpoints).

11. **Per-module CLAUDE.md briefs exist**
    - 9 files, each containing the required template sections.

12. **Frontend agent has the OpenAPI reference**
    - `DOCS/contracts/README.md` exists and explains the workflow.

13. **No secrets in the repo**
    - Run `gitleaks detect --source .`
    - PASS criterion: no findings.

REPORT FORMAT

Produce `PHASE_0_REPORT.md` at the repo root containing:
- A table of every check above with PASS/FAIL and evidence.
- A "Blockers" section listing every FAIL and the prompt number that should fix it.
- A "Sign-off" section: if every check is PASS, state "Phase 0 complete; Phase 1 may start."

FORBIDDEN
- Do NOT modify any code in this prompt — you are auditing, not building.
- Do NOT mark a check PASS without command output as evidence.

Output: contents of `PHASE_0_REPORT.md`.
```

---

## Appendix — Operating notes

### Running these prompts with Claude Code

- Run each prompt in a **fresh** Claude Code session so the agent's context is the prompt + the repo only — not the prior session's chatter.
- Before launching, ensure the repo is clean (`git status` shows no uncommitted work).
- After each prompt, **commit the changes on a feature branch** (e.g., `phase-0/scaffold`, `phase-0/common-base`, …) and merge to `main` only after local CI passes.
- If a prompt fails, the failure log + the original prompt is the next session's input — do not summarize. Agents catch their own mistakes faster from raw failure output.

### Order matters

```
1 → 2 → 3 → 4 → 5    (common module, sequential because each depends on the prior)
6                    (Modulith setup; needs common to exist)
7                    (ArchUnit; needs Modulith conventions)
8                    (Events; needs Modulith)
9                    (Use-case interfaces; needs common money types)
10                   (PaymentProvider port; needs common observability)
11                   (CI; needs everything compiling)
12                   (CLAUDE.md briefs; needs §3.3 contracts to be real)
13                   (Acceptance gate)
```

Some pairs can theoretically run in parallel (e.g., 7 and 8 are independent), but in practice the orchestration overhead exceeds the time saved on tasks of this size. Run sequentially.

### What to do if an agent ignores a "FORBIDDEN" rule

Reject the PR. Re-run the prompt with a one-line addendum: `"Your previous attempt violated FORBIDDEN rule X. Do not repeat."` Do not negotiate; the FORBIDDEN list exists because each rule represents a class of bug that's hard to spot in review.

### When Phase 0 is done

Run Prompt 13. If it produces a PHASE_0_REPORT.md with all PASS, you can launch Phase 1 Wave A's five agents in parallel.

---

*Last updated: 2026-05-05.*
