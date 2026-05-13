# Phase 1 Wave D — Track 1: admin auth + super admin seed

> **Branch:** `phase-1/wave-d-admin-auth` — single branch with **three sequential sub-prompts (1a → 1b → 1c)** committed inside it. Each sub-prompt commits independently.
> 
> **Scope:** close the PRD §4.3 ↔ TECH_SPEC §2.1 ↔ code drift that left admin endpoints unreachable through Waves A–C. Adds `AdminProfile.adminTier`, the four admin auth use cases, `SuperAdminBootstrap`, and `JwtAuthorizationFilter` alongside the existing `ApiKeyAuthFilter`.
> 
> **Read first (every sub-prompt):** [Wave D index](../PHASE_1_WAVE_D_PROMPTS.md) — cross-cutting decisions; `DOCS/features/identity/PRD.md §3.3, §4.3, §6`; `DOCS/features/identity/TECH_SPEC.md §2.1, §3.1, §3.2, §4.3, §4.4, §5`; `ARCHITECTURE.md §17` (filter chain overview); `conflux-identity/CLAUDE.md`.

Sub-prompts:

1. [1a — admin tier schema](#prompt-1a--admin-tier-schema)
2. [1b — use cases + super-admin bootstrap](#prompt-1b--admin-use-cases--bootstrap)
3. [1c — JWT filter + SecurityConfig wiring](#prompt-1c--jwt-filter--integration)

---

## Prompt 1a — admin tier schema

```markdown
You are starting Phase 1 Wave D Track 1 on branch `phase-1/wave-d-admin-auth`.
This is the FIRST of THREE sequential sub-prompts (1a → 1b → 1c) that build the
admin auth surface on the SAME branch. After this sub-prompt commits, the next
session runs Prompt 1b on the same branch.

Your sub-scope: schema-only. Add the `admin_tier` column to `admin_profiles`,
update the `AdminProfile` entity, add the `AdminTier` enum. No use cases, no
controllers, no filter — those come in 1b and 1c.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (full — especially cross-cutting decisions #1, #2, #6)
- DOCS/features/identity/TECH_SPEC.md §2.1 (canonical schema)
- DOCS/features/identity/PRD.md §4.3 (tier semantics)
- conflux-application/src/main/resources/db/migration/V1001__identity_schema.sql (the original schema you're extending)
- conflux-identity/src/main/java/pay/conflux/backend/identity/entity/AdminProfile.java
- conflux-identity/src/main/java/pay/conflux/backend/identity/enums/UserType.java (template for the new enum's placement)

WORK ONLY IN
- conflux-application/src/main/resources/db/migration/V1017__identity_admin_tier.sql (new)
- conflux-identity/src/main/java/pay/conflux/backend/identity/enums/AdminTier.java (new)
- conflux-identity/src/main/java/pay/conflux/backend/identity/entity/AdminProfile.java (add the field)
- conflux-identity/src/test/java/pay/conflux/backend/identity/entity/AdminProfileTest.java (extend or create)
- conflux-application/src/test/java/pay/conflux/backend/identity/migration/V1017MigrationTest.java (new — Testcontainers Postgres)

DO NOT TOUCH
- Any other Flyway migration.
- `User`, `MerchantProfile`, or any other entity.
- Any use case, controller, mapper, or filter.
- `conflux-common`, `conflux-application/.../config/SecurityConfig.java`, the locked event records.
- Root `pom.xml` (the jjwt dep is added in 1b, not here).

DELIVERABLES

1. `AdminTier.java` enum in `pay.conflux.backend.identity.enums`:
   ```java
   public enum AdminTier { VIEWER, MANAGER, SUPER }
   ```
   Javadoc references PRD §4.3 and the inheritance rule ("higher tiers strictly
   inherit lower-tier authorities").

2. `V1017__identity_admin_tier.sql`:
   ```sql
   -- Wave D Track 1 sub-prompt 1a: add admin_tier column to admin_profiles.
   -- Existing rows default to VIEWER per DEVELOPMENT_WORKFLOW.md §4.4 guardrails —
   -- a separate manual promotion is required to elevate them to MANAGER or SUPER.
   ALTER TABLE admin_profiles
       ADD COLUMN admin_tier VARCHAR(16) NOT NULL DEFAULT 'VIEWER';
   ALTER TABLE admin_profiles
       ADD CONSTRAINT admin_profiles_admin_tier_check
       CHECK (admin_tier IN ('VIEWER','MANAGER','SUPER'));
   ```
   The `NOT NULL DEFAULT 'VIEWER'` lets the migration run on any existing
   environment without backfill scripts. The named CHECK constraint lets future
   migrations target it by name (mirrors the V1016 pattern from Wave C).

3. `AdminProfile.java` — add the field with `@Enumerated(EnumType.STRING)` and
   `@Column(name = "admin_tier", nullable = false)`. Default in Java to
   `AdminTier.VIEWER`. Keep field order alphabetical-ish; place after
   `employeeId` so the entity reads in the same order as the migration.

4. (No repository changes — `AdminProfileRepository.findByUserIdAndDeletedFalse`
   from Wave A already returns the full row including the new field.)

TESTS (≥ 1 unit test per new artifact; no per-module gate drop)

- `AdminTierTest` — `assertThat(AdminTier.values()).containsExactly(VIEWER, MANAGER, SUPER)`. Hardcoded count tripwire forces the next change to be deliberate.
- `AdminProfileTest` — extend the existing test (or create one): persist an `AdminProfile` with `adminTier = SUPER`, read it back, assert the enum round-trips. Persist with the default (no explicit set), assert it reads back as `VIEWER`.
- `V1017MigrationTest` (Testcontainers Postgres in `conflux-application`) — apply V1001..V1017, insert a row with `admin_tier='SUPER'`, assert success; insert one with `admin_tier='OWNER'`, assert constraint violation. Also assert an existing pre-migration row (insert via raw SQL with V1001..V1016 applied, then run V1017) gets the `VIEWER` default.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-identity,conflux-application -am verify` BUILD SUCCESS.
- No per-module JaCoCo gate drop.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Single commit: `feat(identity): admin tier schema (wave-d 1a)`.

FORBIDDEN
- Implementing any new use case, controller, mapper, DTO, or filter.
- Editing `User` / `MerchantProfile` / any other entity.
- Editing prior migrations.
- Adding a root-pom dependency.
- Backfilling existing rows to anything other than `VIEWER`.

Output: file tree of additions, the V1017 SQL diff, the `AdminProfile` field diff, JaCoCo tail.
```

---

## Prompt 1b — admin use cases + bootstrap

```markdown
You are continuing Wave D Track 1 on branch `phase-1/wave-d-admin-auth`. Prompt
1a is merged into this branch as a prior commit. Your sub-scope: the four admin
use cases (Authenticate, Create, Update tier, Disable), the JWT-issuing
`TokenService`, the `SuperAdminBootstrap` runner, the admin management
controller, and DTOs. The JWT verification filter and SecurityConfig wiring
come in 1c.

This is the ONLY sub-prompt in Wave D authorized to add root-pom dependencies,
per cross-cutting decision #3.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (full)
- DOCS/features/identity/TECH_SPEC.md §3.1 (the four new use cases), §3.2 (event reuse), §4.4 (bootstrap contract)
- DOCS/features/identity/PRD.md §3.3, §4.3, §6 (super admin AC)
- The 1a commit on this branch (AdminTier enum + V1017 migration)
- conflux-identity/src/main/java/pay/conflux/backend/identity/usecase/impl/AuthenticateUserUseCaseImpl.java (the existing stub-token flow you're replacing)
- conflux-identity/src/main/java/pay/conflux/backend/identity/dto/AuthToken.java (the stub record — replace cleanly)
- conflux-identity/src/main/java/pay/conflux/backend/identity/constant/IdentityRoutes.java (add new routes here)
- conflux-common/src/main/java/pay/conflux/backend/common/crypto/HmacSigner.java (the existing HMAC primitive — read but do not edit)

WORK ONLY IN
- conflux-identity/src/main/java/pay/conflux/backend/identity/{usecase,usecase.impl,controller,controller.impl,dto,mapper,constant,bootstrap}/...
- conflux-identity/src/test/java/...
- conflux-application/src/test/java/pay/conflux/backend/identity/... (integration tests for bootstrap + use cases)
- Root pom.xml (jjwt deps in `<dependencyManagement>` ONLY)
- conflux-identity/pom.xml (import jjwt deps, no version)
- conflux-application/src/main/resources/application.yml + application-test.yml (add `conflux.identity.super-admin.*` and `conflux.identity.jwt.*` config blocks; do NOT edit any other section)

DO NOT TOUCH
- conflux-common/ (read-only).
- conflux-identity/src/main/java/pay/conflux/backend/identity/events/ (locked event records — `UserBlockedEvent` is reused as-is per TECH_SPEC §3.2).
- conflux-application/.../config/SecurityConfig.java (filter wiring is 1c's job).
- conflux-application/.../security/ApiKeyAuthFilter.java (Wave B 8c artifact — read but do not edit).
- Any other module's pom.xml beyond root + conflux-identity/pom.xml.

DELIVERABLES

1. **Root pom + identity pom — jjwt deps.** Add to root `<dependencyManagement>`:
   ```xml
   <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
   <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
   <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
   ```
   In `conflux-identity/pom.xml`, import all three (no `<version>` — managed at root).

2. **`JwtTokenService`** in `pay.conflux.backend.identity.support`:
   - `@Component @RequiredArgsConstructor` with `@Value("${conflux.identity.jwt.secret}")` HMAC key and `@Value("${conflux.identity.jwt.expiry-minutes:60}")`.
   - `@PostConstruct verify()` — fails fast if secret is blank or shorter than 32 bytes after Base64 decode.
   - `String issue(User user, AdminProfile adminProfileOrNull)` — builds a JWT with claims `sub=userId`, `userType=MERCHANT|ADMIN`, and (when `userType == ADMIN`) `tier=VIEWER|MANAGER|SUPER`. `iat = now`, `exp = now + expiryMinutes`. Signs with HS256.
   - `JwtClaims parse(String token)` — throws `UnauthorizedException` on signature failure, expiry, or malformed JWT. The `parse` method is consumed by `JwtAuthorizationFilter` in 1c; ship it now so 1c can wire it without API churn.
   - **DELETE** the stub `AuthToken` record and the stub-HMAC code path in `AuthenticateUserUseCaseImpl`. Replace its token issuance with a call to `jwtTokenService.issue(...)`. Update `LoginResponse` to carry the issued JWT string verbatim.

3. **`AuthenticateUserUseCase`** — update behavior (NOT signature):
   - Step 5 (token issuance) now calls `JwtTokenService.issue(user, adminProfile)`. If `user.userType == ADMIN`, load `AdminProfile` (must exist — fail with `IllegalStateException` if missing, since the schema FK guarantees it).
   - All other steps (regex detection, BCrypt verify, status check) unchanged.
   - **No new use-case interface** — the same `AuthenticateUserUseCase` serves both merchant and admin login, with the token claim set differing per `userType`. The shared login endpoint `POST /api/v1/auth/login` remains the single entry point.

4. **Four new use cases** in `pay.conflux.backend.identity.usecase` + `usecase.impl`:
   - `CreateAdminUseCase` — input `CreateAdminRequest(identifier, password, fullName /* department */, employeeId, adminTier)`. Step 1 detect identifierType via `common.util.IdentifierDetector` (admin identifiers are EMAIL by convention but PHONE is allowed). Step 2 reject if `existsByIdentifierAndIdentifierTypeAndDeletedFalse`. Step 3 BCrypt the password. Step 4 inside `@Transactional`: create `User(userType=ADMIN, status=ACTIVE)` + `AdminProfile(adminTier=<requested>)`. Step 5 reject in the controller — see DELIVERABLE 5 — if the requested tier is `SUPER` and the **caller** is not already SUPER. The use case itself accepts any tier; the SUPER-only gate lives at the controller via `@PreAuthorize("hasAuthority('SUPER_ADMIN')")`. Returns `AdminProfileDto`.
   - `UpdateAdminTierUseCase` — input `(targetUserId, newTier)`. Inside `@Transactional`: load target `AdminProfile`; if `newTier` is the same as current, return idempotently. If the target is currently SUPER and `newTier != SUPER`, perform the **last-SUPER guard**: count `adminTier=SUPER AND user.status=ACTIVE AND user.deleted=false`; if count == 1, throw `InvalidOperationStateException("Cannot demote the last SUPER admin")`. Otherwise save. Returns `AdminProfileDto`.
   - `DisableAdminUseCase` — input `targetUserId`. Inside `@Transactional`: load target `User`; reject with `InvalidOperationStateException` if `target == caller` (self-disable not allowed); if target is SUPER, run the same last-SUPER guard (counting active SUPER admins, must be > 1 to proceed). Set `User.status = BLOCKED`. Publish `UserBlockedEvent` exactly once (idempotent: a re-call on an already-BLOCKED user returns success without re-publishing — Wave A 1b already established this pattern in `BlockUserUseCase`; re-use the same idempotency check).
   - `ListAdminsUseCase` — input `PaginationRequest + AdminTier filter (optional)`. Returns `Page<AdminProfileSummaryDto>`. Read-only; no transaction needed beyond Spring Data's default.

5. **`AdminManagementController`** interface + Impl in `pay.conflux.backend.identity.controller`:
   - `GET    /api/v1/admin/admins` → `@PreAuthorize("hasAuthority('ADMIN_VIEWER')")` (any admin can list).
   - `POST   /api/v1/admin/admins` (body: `CreateAdminRequest`) → `@PreAuthorize("hasAuthority('SUPER_ADMIN')")`.
   - `PATCH  /api/v1/admin/admins/{id}/tier` (body: `UpdateAdminTierRequest(newTier)`) → `@PreAuthorize("hasAuthority('SUPER_ADMIN')")`.
   - `POST   /api/v1/admin/admins/{id}/disable` → `@PreAuthorize("hasAuthority('SUPER_ADMIN')")`.
   - `GET    /api/v1/admin/me` → returns the caller's `AdminProfileDto` (uses `SecurityUtils.currentAdminId()` after 1c wires the filter; for 1b's tests, mark this endpoint TODO and ship it as a stub returning 501 — 1c completes the wiring).
   - Mapping annotations on the interface, `@PreAuthorize` + `@RequestBody @Valid` on the Impl, per `ARCHITECTURE.md §5`.
   - Add new constants to `IdentityRoutes`: `ADMIN_ADMINS`, `ADMIN_ADMINS_TIER`, `ADMIN_ADMINS_DISABLE`, `ADMIN_ME`.

6. **DTOs**: `CreateAdminRequest` (validated: `@NotBlank`, `@Size`, `@SafeString`, `@NotNull AdminTier`), `UpdateAdminTierRequest`, `AdminProfileDto`, `AdminProfileSummaryDto`. Sensitive fields (`passwordHash`, `mfaSecret`) MUST NOT be in any DTO — verify via mapper test.

7. **`SuperAdminBootstrap`** in `pay.conflux.backend.identity.bootstrap`:
   - `@Component` implementing `ApplicationRunner`, ordered with `@Order(Ordered.HIGHEST_PRECEDENCE + 100)` so it runs after Flyway (which is `HIGHEST_PRECEDENCE` by default in Spring Boot) but before any other startup work.
   - `@Value` injects `${conflux.identity.super-admin.identifier:}` and `${conflux.identity.super-admin.password:}` (both default to empty string).
   - Behavior per TECH_SPEC §4.4 (six steps): fail-fast if both env vars blank and no SUPER admin exists; idempotent create if no row with the configured identifier; rotate hash if existing row's BCrypt doesn't match configured password; no-op if everything matches.
   - Writes directly via `UserRepository` + `AdminProfileRepository` + `BCryptPasswordEncoder`. Does NOT use `CreateAdminUseCase` (chicken-and-egg — no authenticated SUPER caller exists on a fresh DB).
   - Log at INFO: which path was taken ("created", "rotated", "noop", "fail-fast"). Never log the password (plaintext or hash).

8. **YAML config blocks** in `application.yml` (env-var-driven):
   ```yaml
   conflux:
     identity:
       jwt:
         secret: ${JWT_SECRET}
         expiry-minutes: ${JWT_EXPIRY_MINUTES:60}
       super-admin:
         identifier: ${SUPER_ADMIN_IDENTIFIER:}
         password: ${SUPER_ADMIN_PASSWORD:}
   ```
   In `application-test.yml`, set `conflux.identity.jwt.secret` to a fixed
   Base64-encoded 32-byte test fixture (e.g.,
   `MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=`) so integration tests don't
   need env vars. Leave the super-admin block blank in test profile — tests
   that exercise the bootstrap set the props per-test via `@DynamicPropertySource`
   or `@TestPropertySource`.

TESTS

Unit (use cases):
- `CreateAdminUseCaseImplTest` — happy path; duplicate identifier rejection; BCrypt verification on the stored hash.
- `UpdateAdminTierUseCaseImplTest` — every transition (V→M, M→S, S→M, S→V, etc.); idempotent no-op when newTier == currentTier; last-SUPER guard fires when count==1 and rejects S→{V,M}.
- `DisableAdminUseCaseImplTest` — happy path; self-disable rejected; last-SUPER guard rejects disabling the last SUPER; idempotency on already-BLOCKED user does not re-publish `UserBlockedEvent` (verify with Mockito).
- `JwtTokenServiceTest` — issue+parse round-trip for MERCHANT (no tier claim) and ADMIN+SUPER (with tier claim); expired token rejected; malformed token rejected; tampered signature rejected.

Integration (`@SpringBootTest` + Testcontainers Postgres):
- `SuperAdminBootstrapIT` covering all five scenarios from TECH_SPEC §5:
  1. Fresh DB + no env vars → application context fails to start with `IllegalStateException` containing the documented message. Use `assertThatThrownBy(() -> new SpringApplicationBuilder(...).run())`.
  2. Fresh DB + env vars set → exactly one SUPER admin row exists; identifier matches; BCrypt verifies against the configured password.
  3. Re-boot with same env vars → row unchanged (compare row's `updatedAt` from boot 1 vs boot 2).
  4. Re-boot with different password → same row, hash updated, log emits "rotated" message (capture via `LogCaptor` or similar).
  5. Pre-existing SUPER admin (seeded via `@Sql`), env vars cleared → boots successfully, no error.
- `AuthenticateUserUseCaseIT` — admin login returns a JWT whose payload (decoded) contains `userType=ADMIN` and `tier=<expected>`. Merchant login returns a JWT with `userType=MERCHANT` and no `tier` claim.

ACCEPTANCE CRITERIA (this sub-prompt)
- All from 1a still hold.
- `mvn -pl conflux-identity,conflux-application -am verify` BUILD SUCCESS.
- No per-module JaCoCo gate drop; the conflux-identity module's coverage stays at its Wave A end-state (≥ 80% line, ≥ 70% branch).
- All four new use cases have at least one unit test + one integration test.
- Single commit: `feat(identity): admin use cases + super admin bootstrap (wave-d 1b)`.

FORBIDDEN
- Editing `ApiKeyAuthFilter`, `SecurityConfig`, or any other Wave B/C filter — that's 1c's job.
- Adding any root-pom dep beyond the three jjwt entries.
- Returning the JWT secret, the super-admin password, or any plaintext credential on any endpoint or in any log line.
- Distinguishing "user not found" from "wrong password" in `AuthenticateUserUseCase` — the Wave A 1a contract still holds.
- Using `CreateAdminUseCase` for the bootstrap — direct repository writes only.

Output: file tree, list of new `IdentityRoutes`, sample admin-login JSON (request + response with token decoded), sample bootstrap log line for each of the five scenarios, JaCoCo tail.
```

---

## Prompt 1c — JWT filter + integration

```markdown
You are completing Wave D Track 1 on branch `phase-1/wave-d-admin-auth`. Prompts
1a and 1b are merged into this branch. Your sub-scope: `JwtAuthorizationFilter`,
its integration into `SecurityConfig` alongside the existing `ApiKeyAuthFilter`,
the `/admin/me` endpoint completion, and the full authority-matrix integration
tests.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (full — especially cross-cutting decision #4 on token shape)
- DOCS/features/identity/TECH_SPEC.md §4.3 (authority resolution table)
- ARCHITECTURE.md §17 (filter chain overview)
- conflux-application/src/main/java/pay/conflux/backend/application/security/ApiKeyAuthFilter.java (the existing filter you're sitting next to)
- conflux-application/src/main/java/pay/conflux/backend/application/config/SecurityConfig.java (current chain)
- The 1a + 1b commits on this branch (AdminTier, JwtTokenService, use cases, bootstrap)
- conflux-common/src/main/java/pay/conflux/backend/common/security/AuthenticatedPrincipal.java (the principal record you populate)
- conflux-common/src/main/java/pay/conflux/backend/common/security/SecurityUtils.java (where `currentAdminId()` reads from)

WORK ONLY IN
- conflux-application/src/main/java/pay/conflux/backend/application/security/JwtAuthorizationFilter.java (new)
- conflux-application/src/main/java/pay/conflux/backend/application/security/AdminAuthorityResolver.java (new — pure function from User+AdminProfile → authority list)
- conflux-application/src/main/java/pay/conflux/backend/application/config/SecurityConfig.java (add the new filter to the chain)
- conflux-identity/src/main/java/pay/conflux/backend/identity/controller/impl/AdminManagementControllerImpl.java (replace the 501 stub on `/admin/me`)
- conflux-application/src/test/java/pay/conflux/backend/application/security/JwtAuthorizationFilterTest.java (new)
- conflux-application/src/test/java/pay/conflux/backend/identity/AdminAuthorityMatrixIT.java (new — end-to-end authority assertions)
- conflux-application/src/test/java/pay/conflux/backend/application/security/FilterCoexistenceIT.java (new — proves the two filters are mutually exclusive)

DO NOT TOUCH
- `ApiKeyAuthFilter` (Wave B 8c — read only; the JWT filter must coexist without modifying it).
- Any use case, mapper, or DTO from 1a/1b.
- `JwtTokenService` (1b's deliverable — `parse(String)` is consumed as-is).
- Any module's pom.xml.
- Any Flyway migration.

DELIVERABLES

1. **`AdminAuthorityResolver`** — small, pure utility class:
   ```java
   public static List<GrantedAuthority> resolve(UserType userType, AdminTier tierOrNull) {
       if (userType == UserType.MERCHANT) return List.of(new SimpleGrantedAuthority("MERCHANT"));
       // userType == ADMIN — tier MUST be non-null per FK
       return switch (tierOrNull) {
           case VIEWER  -> List.of(new SimpleGrantedAuthority("ADMIN_VIEWER"));
           case MANAGER -> List.of(new SimpleGrantedAuthority("ADMIN_VIEWER"),
                                   new SimpleGrantedAuthority("ADMIN_MANAGER"));
           case SUPER   -> List.of(new SimpleGrantedAuthority("ADMIN_VIEWER"),
                                   new SimpleGrantedAuthority("ADMIN_MANAGER"),
                                   new SimpleGrantedAuthority("SUPER_ADMIN"));
       };
   }
   ```
   The inheritance is baked in here so callers never have to enumerate it.

2. **`JwtAuthorizationFilter`** in `pay.conflux.backend.application.security`:
   - Extends `OncePerRequestFilter`, `@Component`, constructor-inject `JwtTokenService`, `UserRepository`, `AdminProfileRepository`, `ObjectMapper`.
   - `shouldNotFilter(request)`: same whitelist as `ApiKeyAuthFilter` (factor out a `SecurityWhitelist` constant class if it doesn't already exist, but DO NOT edit `ApiKeyAuthFilter` — re-use the constant by referencing it from there if it's already public, otherwise duplicate the list with a TODO comment to consolidate in a future cleanup).
   - In `doFilterInternal`:
     1. If `SecurityContextHolder.getContext().getAuthentication()` is already set, pass through (no double-auth).
     2. Read the `Authorization: Bearer <value>` header. If absent or not Bearer, pass through (let `ApiKeyAuthFilter` handle `X-API-Key` callers).
     3. Check the value against the JWT regex `^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`. If it doesn't match, pass through (it's an API key shape — `ApiKeyAuthFilter` will pick it up).
     4. If JWT shape: call `jwtTokenService.parse(token)` (which throws `UnauthorizedException` on signature/expiry/malformed). On failure, write 401 via the same `ApiResult.error` shape `ApiKeyAuthFilter` uses, and return (do NOT pass through — a malformed JWT must not silently fall through to API-key auth, because the caller clearly intended JWT auth).
     5. On success: load `User` by `claims.sub` (the userId UUID). If user is `null` or `status != ACTIVE`, write 401. If `userType == ADMIN`, load the `AdminProfile`; FK guarantees existence — if missing, log error and write 401. Call `AdminAuthorityResolver.resolve(userType, adminTier)` to compute authorities.
     6. Build `AuthenticatedPrincipal` with `userId`, `userType`, `merchantId=null for admin`, `businessId=null`, `environment=null` (admins are environment-agnostic — the principal record's Objects.requireNonNull only enforces userId+userType, so the other three can be null).
     7. Set `SecurityContextHolder` with `UsernamePasswordAuthenticationToken(principal, null, authorities)`. Pass through.
   - **Filter order:** add to the chain BEFORE `ApiKeyAuthFilter` so JWT-shaped tokens get the first crack; API-key-shaped tokens fall through. Do not register both filters in a way that double-auths.

3. **`SecurityConfig.java`** — minimal edit: add `.addFilterBefore(jwtAuthorizationFilter, ApiKeyAuthFilter.class)` after the existing `.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)`. The whitelist remains unchanged (1b didn't add any public admin endpoints). Confirm via test that `/api/v1/admin/admins` is NOT in the public whitelist.

4. **`AdminManagementControllerImpl.me()`** — replace the 1b stub:
   ```java
   @Override
   @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
   public ResponseEntity<ApiResult<AdminProfileDto>> me() {
       UUID adminUserId = SecurityUtils.currentAdminId()
           .orElseThrow(() -> new UnauthorizedException("No admin context"));
       return ApiResult.ok(getAdminProfileUseCase.execute(adminUserId));
   }
   ```
   If `GetAdminProfileUseCase` doesn't exist yet, add it now (small read-only use case — `findByUserIdAndDeletedFalse` + map). Wire into the controller.

TESTS

Unit:
- `AdminAuthorityResolverTest` — exhaustive table test covering all four (userType, tier) combinations. Assert exact authority lists (order-insensitive).
- `JwtAuthorizationFilterTest` (MockMvc-free, plain filter unit test with a stub `FilterChain`):
  - Missing Authorization header → pass through, context unset.
  - `X-API-Key: <opaque>` only → pass through (no JWT to parse), context unset.
  - `Authorization: Bearer abc123` (no dots — API-key shape) → pass through, context unset.
  - `Authorization: Bearer <valid JWT for MERCHANT>` → context set with `MERCHANT` authority.
  - `Authorization: Bearer <valid JWT for ADMIN+SUPER>` → context set with `ADMIN_VIEWER + ADMIN_MANAGER + SUPER_ADMIN` authorities.
  - `Authorization: Bearer <expired JWT>` → 401, context unset, response is `ApiResult.error` shape.
  - `Authorization: Bearer <JWT signed with wrong secret>` → 401, context unset.
  - `Authorization: Bearer <JWT for BLOCKED user>` → 401, context unset.

Integration (`@SpringBootTest` + Testcontainers Postgres + real HTTP via `MockMvc` or `WebTestClient`):
- `AdminAuthorityMatrixIT` — for each tier (VIEWER, MANAGER, SUPER) and one MERCHANT, log in via `POST /api/v1/auth/login`, extract the JWT, then hit each endpoint:
  | Endpoint | MERCHANT | VIEWER | MANAGER | SUPER |
  |---|---|---|---|---|
  | `GET /admin/merchants` | 403 | 200 | 200 | 200 |
  | `POST /admin/merchants/{id}/verify` | 403 | 403 | 200 | 200 |
  | `GET /admin/admins` | 403 | 200 | 200 | 200 |
  | `POST /admin/admins` | 403 | 403 | 403 | 200 |
  | `PATCH /admin/admins/{id}/tier` | 403 | 403 | 403 | 200 |
  | `POST /admin/admins/{id}/disable` | 403 | 403 | 403 | 200 |
  | `GET /admin/me` | 403 | 200 | 200 | 200 |
  Assert every cell of the matrix.
- `FilterCoexistenceIT` — issue a merchant JWT (via login), and separately mint a real merchant API key (via the existing provisioning use case). Send three requests to `GET /api/v1/business`:
  1. JWT bearer → 200, response indicates JWT-resolved merchant.
  2. API-key bearer → 200, response indicates API-key-resolved merchant.
  3. Both headers present (JWT in Authorization, API key in `X-API-Key`) → JWT wins (filter order); request succeeds. Optional: assert via log capture that only `JwtAuthorizationFilter` set the context.

ACCEPTANCE CRITERIA (this sub-prompt — final for Track 1)
- All from 1a + 1b still hold.
- `mvn -pl conflux-identity,conflux-application -am verify` BUILD SUCCESS.
- JaCoCo aggregate ≥ 80% line, ≥ 70% branch.
- ArchUnit + Modulith green.
- The Wave A acceptance gate's tests are still green (no regression in merchant-side auth).
- Single commit: `feat(application,identity): JWT filter + admin authority matrix (wave-d 1c)`.

FORBIDDEN
- Modifying `ApiKeyAuthFilter`.
- Adding `ROLE_` prefix to any granted authority.
- Skipping the JWT-shape check (regex) — letting a malformed JWT fall through to API-key auth is a vulnerability (a JWT with a corrupted signature must 401, not silently get reinterpreted as an API key).
- Adding any new root-pom dep.
- Granting authorities other than the four-string namespace (`MERCHANT`, `ADMIN_VIEWER`, `ADMIN_MANAGER`, `SUPER_ADMIN`).

Output: file tree, the authority matrix table from the IT showing all cells PASS, JaCoCo tail (line + branch per module), the diff to `SecurityConfig` and `IdentityRoutes`.
```
