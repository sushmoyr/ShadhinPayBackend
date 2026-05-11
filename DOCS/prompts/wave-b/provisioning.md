# Phase 1 Wave B — `provisioning` module prompts

> **Branch:** `phase-1/provisioning` — run all three sub-prompts sequentially in the same git worktree on the same branch.
> **Scope:** ship `Business`, `VendorConfig`, `ApiKey` entities; the five inbound use cases (`CreateBusinessUseCase`, `ConfigureVendorUseCase`, `GenerateApiKeyUseCase`, `ValidateApiKeyUseCase`, `UpdateWebhookUseCase`); the two locked cross-module impls (`GetBusinessByApiKeyUseCaseImpl`, `GetVendorConfigUseCaseImpl`); the Modulith event listeners (`MerchantVerifiedEvent` → auto-create default business; `UserBlockedEvent` → cache evict); merchant + admin controllers; Redis-backed API-key and vendor-config caches.
> **Read first (every sub-prompt):** the [Wave B index](../PHASE_1_WAVE_B_PROMPTS.md) — cross-cutting decisions; `conflux-provisioning/CLAUDE.md`; `DOCS/features/provisioning/PRD.md`; `DOCS/features/provisioning/TECH_SPEC.md`; `PHASE_1_WAVE_A_REPORT.md` § "Locked Wave A Contracts".

Sub-prompts:
1. [7a — schema + entities + write-side use cases](#prompt-7a--provisioning-schema--entities--write-side-use-cases)
2. [7b — cross-module impls + Modulith listeners + Redis cache](#prompt-7b--provisioning-cross-module-impls--modulith-listeners--redis-cache)
3. [7c — controllers + coverage push + jqwik invariants](#prompt-7c--provisioning-controllers--coverage-push--jqwik-invariants)

---

## Prompt 7a — provisioning schema + entities + write-side use cases

```
You are starting the `conflux-provisioning` Wave B module on branch `phase-1/provisioning`. This is the FIRST of THREE sub-prompts (7a → 7b → 7c).

Wave A is on `main` at commit `286cce1` (or later). The two cross-module use-case interfaces (`GetBusinessByApiKeyUseCase`, `GetVendorConfigUseCase`) and their DTOs (`BusinessContext`, `VendorConfigDescriptor`) already exist in `conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/usecase/` as interfaces with no impls — DO NOT modify those files. You will provide the impls in sub-prompt 7b.

Your sub-scope (7a): persistence, entities, mappers, the FIVE write-side inbound use cases from TECH_SPEC §3.1, and migrations V1012 + V1013.

READ FIRST
- ARCHITECTURE.md
- DEVELOPMENT_WORKFLOW.md §4.1, §7.2
- DOCS/prompts/PHASE_1_WAVE_B_PROMPTS.md "Cross-cutting decisions"
- conflux-provisioning/CLAUDE.md
- DOCS/features/provisioning/PRD.md (full)
- DOCS/features/provisioning/TECH_SPEC.md (full)
- conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/usecase/ (the four locked interface/DTO files)
- conflux-common/src/main/java/pay/conflux/backend/common/crypto/EncryptionService.java (used for AES-256-GCM on credentials + webhook secret)
- conflux-identity/src/main/resources/db/migration/V1001__identity_schema.sql (so the FK on businesses.merchant_id resolves to identity.users.id correctly)

WORK ONLY IN
- conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/{entity,repository,dto,mapper,usecase/impl,constant,validator,spec}/...
- conflux-provisioning/src/main/resources/db/migration/...
- conflux-provisioning/src/test/...

DO NOT TOUCH
- The four locked cross-module interface/DTO files in provisioning/usecase/.
- conflux-common/, identity, ledger, risk, quota, adapters, payment-core.
- Root pom.xml.
- DOCS/contracts/openapi.json.
- Existing Wave A migrations.

DELIVERABLES

1. **Flyway migration `V1012__provisioning_schema.sql`** with three tables:
   - `businesses` — `id UUID PK`, `merchant_id UUID NOT NULL REFERENCES identity.users(id)`, `name TEXT NOT NULL`, `display_name TEXT NOT NULL`, `logo_url TEXT`, `status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE'))`, `webhook_url TEXT`, `webhook_secret_encrypted TEXT`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `deleted BOOLEAN NOT NULL DEFAULT false`. Index on `(merchant_id, deleted)`.
   - `vendor_configs` — `id UUID PK`, `business_id UUID NOT NULL REFERENCES businesses(id)`, `vendor TEXT NOT NULL CHECK (vendor IN ('BKASH','NAGAD','ROCKET','UPAY','PATHAO','MCASH','STRIPE','MOCK'))`, `mode TEXT NOT NULL DEFAULT 'PARTNER' CHECK (mode IN ('PARTNER','CUSTOM'))`, `credentials_encrypted JSONB`, `created_at`, `updated_at`. Unique index on `(business_id, vendor)`.
   - `api_keys` — `id UUID PK`, `business_id UUID NOT NULL REFERENCES businesses(id)`, `key_hash TEXT NOT NULL UNIQUE`, `key_prefix TEXT NOT NULL`, `last_four TEXT NOT NULL`, `environment TEXT NOT NULL CHECK (environment IN ('TEST','LIVE'))`, `revoked BOOLEAN NOT NULL DEFAULT false`, `last_used_at TIMESTAMPTZ`, `expires_at TIMESTAMPTZ`, `created_at`, `updated_at`. Unique index on `key_hash`; secondary index on `(business_id, environment, revoked)`.

2. **Flyway migration `V1013__provisioning_seed_partner_vendors.sql`** — empty placeholder for future seed data; commit a one-line comment so the migration is tracked but is a no-op. (Rationale: keeps the migration slot reserved; vendor seed data is real-vendor work, Wave C.)

3. **Entities** (`pay.conflux.backend.provisioning.entity`):
   - `Business`, `VendorConfig`, `ApiKey` — JPA entities. Extend the project's `AuditableAndSoftDeletable` (or `Auditable` if soft-delete isn't on the entity). `@Enumerated(EnumType.STRING)` for `status`, `mode`, `environment`. **No `@Data`.** Use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`. `@ManyToOne(fetch = FetchType.LAZY)` for any associations.

4. **Repositories** (`pay.conflux.backend.provisioning.repository`):
   - `BusinessRepository extends JpaRepository<Business, UUID>, JpaSpecificationExecutor<Business>` — `findByMerchantIdAndDeletedFalse(UUID merchantId)`, `existsByMerchantIdAndDeletedFalse(UUID merchantId)`.
   - `VendorConfigRepository` — `findByBusinessIdAndVendor(UUID businessId, String vendor)`, `findAllByBusinessId(UUID businessId)`.
   - `ApiKeyRepository` — `findByKeyHashAndRevokedFalse(String keyHash)`, `findAllByBusinessIdAndRevokedFalse(UUID businessId)`, `findAllByBusinessIdAndEnvironment(UUID businessId, String environment)`.

5. **DTOs** (`pay.conflux.backend.provisioning.dto`):
   - Requests: `CreateBusinessRequest(@NotBlank String name, String displayName, String logoUrl)`, `ConfigureVendorRequest(@NotBlank String vendor, @NotBlank String mode, Map<String,String> credentials)`, `GenerateApiKeyRequest(@NotBlank String environment, Instant expiresAt)`, `UpdateWebhookRequest(@URL @Pattern(regexp="^https://.*") String webhookUrl)`.
   - Responses: `BusinessDto`, `BusinessSummaryDto`, `VendorConfigDto` (NEVER includes raw credentials — only `mode` + `vendor` + `configured: boolean`), `ApiKeyDto` (returned ONCE on generation with full plaintext `key`), `ApiKeySummaryDto` (subsequent lookups — `keyPrefix`, `lastFour`, `environment`, `lastUsedAt`, no plaintext).

6. **Mapper** (`pay.conflux.backend.provisioning.mapper.ProvisioningMapper`) — MapStruct, `componentModel = "spring"`. **Audit it before committing:** no DTO emitted by this mapper may contain `credentialsEncrypted`, `webhookSecretEncrypted`, or `keyHash` fields.

7. **Five write-side use case impls** in `pay.conflux.backend.provisioning.usecase.impl` (interfaces first if they don't exist in `usecase/` yet — they don't; create them in `usecase/`):
   - `CreateBusinessUseCase` / `CreateBusinessUseCaseImpl` — input `(UUID merchantId, CreateBusinessRequest)`. Generates `webhookSecret` (32-byte random, base64), encrypts via `EncryptionService` with purpose tag `"webhook-secret"`, persists `Business`. Returns `BusinessDto`.
   - `ConfigureVendorUseCase` / `Impl` — input `(UUID businessId, ConfigureVendorRequest)`. If `mode == "CUSTOM"`: serializes `credentials` map to JSON, encrypts with purpose tag `"vendor-credentials"`, upserts `VendorConfig`. If `mode == "PARTNER"`: stores null/empty credentials (PARTNER uses platform credentials). Returns `VendorConfigDto`.
   - `GenerateApiKeyUseCase` / `Impl` — input `(UUID businessId, GenerateApiKeyRequest)`. Generates 32-char random suffix via `SecureRandom`. Plaintext = `sp_live_` or `sp_test_` + suffix. Stores `keyHash = SHA-256(plaintext)`, `keyPrefix`, `lastFour = plaintext.substring(plaintext.length() - 4)`. Returns `ApiKeyDto` with the plaintext **once**. The plaintext must NOT be persisted, logged, or returned by any other method. **LIVE keys**: throw `InvalidOperationStateException` if the merchant's identity status is not `ACTIVE` (you'll need a thin read-through interface — for 7a, do the check via a new injected `MerchantStatusPort` interface you define in this module; 7b's listener-side wiring will be the implementation, OR resolve it via a `UserRepository` lookup that this module is allowed to do via cross-feature use-case interface — since identity exposes nothing for this, **define `MerchantStatusPort` as a new locked-by-Wave-B interface in `provisioning/usecase/MerchantStatusPort.java` and implement it in `provisioning/usecase/impl/IdentityBackedMerchantStatusPort.java`** which does its own JdbcTemplate read against `identity.users(id, status)` — explicitly allowed because it's read-only and goes through a port the provisioning module owns).
   - `ValidateApiKeyUseCase` / `Impl` — input `(String plaintextKey)`. Hashes input, looks up `ApiKey` by hash + `revokedFalse`. On hit: updates `lastUsedAt` (async — fire-and-forget through a transactional `@Async` method or batched write; don't block the hot path). Returns a `ValidatedApiKey(businessId, environment)` record (define it). On miss/revoked/expired: throws `UnauthorizedException` (extends `ApiOperationException`, `ErrorCode.UNAUTHORIZED`, HTTP 401).
   - `UpdateWebhookUseCase` / `Impl` — input `(UUID businessId, UpdateWebhookRequest)`. **Rejects http://** (validation already on DTO, but defense-in-depth). Rotates `webhookSecret` if `?rotate=true` query param flag is included (controller decides; 7c). Stores encrypted. **Fires a "PING" webhook event** to the new URL via the webhook-outbox queue — for 7a, define the queue insert; the actual HTTP dispatcher is in `payment-core` 8b (this is fine — provisioning enqueues, payment-core drains, both via the same `webhook_outbox` table in 8a's migration). For 7a, defer the PING enqueue with a TODO comment if the table doesn't exist yet, and document the deferral in the commit message.

TESTS (target: 55% module coverage on this sub-prompt)

Unit:
- `CreateBusinessUseCaseImpl` — happy path (returns DTO, persists with encrypted webhook secret), missing merchant rejects with `ResourceNotFoundException`.
- `ConfigureVendorUseCaseImpl` — PARTNER mode stores null credentials, CUSTOM mode stores encrypted blob (verify via captor: the JSON written to `credentialsEncrypted` matches the AES-GCM ciphertext shape, **not** the plaintext).
- `GenerateApiKeyUseCaseImpl` — happy path generates correct prefix, returns plaintext exactly once, stores SHA-256 hash. LIVE key blocked when merchant status != ACTIVE.
- `ValidateApiKeyUseCaseImpl` — hit returns `(businessId, environment)`, miss throws `UnauthorizedException`.
- `UpdateWebhookUseCaseImpl` — http:// URL rejected (Bean Validation level — covered in controller test in 7c).

Integration (Testcontainers Postgres):
- Flyway migrations apply cleanly.
- `BusinessRepository`/`VendorConfigRepository`/`ApiKeyRepository` smoke tests against real schema.

ACCEPTANCE CRITERIA (this sub-prompt)
- `mvn -pl conflux-provisioning -am verify` BUILD SUCCESS.
- JaCoCo ≥ 55%.
- All migrations apply on a fresh DB.
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Mapper audit: `grep -r "credentialsEncrypted\|webhookSecretEncrypted\|keyHash" conflux-provisioning/src/main/java/pay/conflux/backend/provisioning/dto/` → empty.
- Commit: `feat(provisioning): schema + entities + write-side use cases (7a)`.

FORBIDDEN
- Implementing `GetBusinessByApiKeyUseCase` / `GetVendorConfigUseCase` (7b).
- Implementing the `MerchantVerifiedEvent` or `UserBlockedEvent` listeners (7b).
- Adding Redis cache wiring (7b).
- Implementing any controller (7c).
- Modifying any locked cross-module interface or DTO.
- Modifying the `ApiResult<T>` envelope, `ErrorCode` enum, or `Money` record.
- Adding a root-pom dep.
- Editing existing Wave A migrations.

Output: file tree, sample encrypted-credentials blob (showing AES-GCM ciphertext shape, NOT plaintext), JaCoCo tail.
```

---

## Prompt 7b — provisioning cross-module impls + Modulith listeners + Redis cache

```
You are continuing the `conflux-provisioning` Wave B module on branch `phase-1/provisioning`. Sub-prompt 7a is committed. Your sub-scope (7b): the two locked cross-module use-case impls, the Spring Modulith event listeners, and the Redis-backed lookup caches on the hot path.

READ FIRST
- The 7a commit
- DOCS/features/provisioning/TECH_SPEC.md §3.2 (event listeners), §5.1 (caching), §5.2 (isolation)
- conflux-identity/src/main/java/pay/conflux/backend/identity/events/{MerchantVerifiedEvent,UserBlockedEvent}.java
- Spring Modulith docs for `@ApplicationModuleListener` (Context7 MCP if available)

DELIVERABLES

1. **`GetBusinessByApiKeyUseCaseImpl`** (`pay.conflux.backend.provisioning.usecase.impl`):
   - `@UseCase`. Hot-path interface — must complete in < 50 ms p95 (per PRD §5).
   - Hashes input `apiKey` to SHA-256.
   - Cache key: `provisioning:apikey:{keyHash}` → JSON-serialized `BusinessContext` record.
   - On cache hit: deserialize and return.
   - On cache miss: `ApiKeyRepository.findByKeyHashAndRevokedFalse` → `BusinessRepository.findById`; build `BusinessContext(businessId, merchantId, environment, webhookUrl)`. Write to cache with TTL = `300s` (5 min). Asynchronously update `ApiKey.lastUsedAt` (transactional fire-and-forget).
   - On miss / revoked / expired / business INACTIVE: throw `UnauthorizedException`.
   - **Tenant isolation invariant:** never returns a `BusinessContext` for a business in `INACTIVE` status. Cache invalidation on status change is handled by the `UserBlockedEvent` listener (below) and the `evictApiKeyCache(UUID businessId)` helper.

2. **`GetVendorConfigUseCaseImpl`** (same package):
   - `@UseCase`. Same hot-path budget.
   - Cache key: `provisioning:vendorconfig:{businessId}:{vendor}` → JSON-serialized `VendorConfigDescriptor`.
   - On cache miss: `VendorConfigRepository.findByBusinessIdAndVendor`. The `credentialsRefs` map returned is **opaque pointer form**, not the plaintext credentials. For PARTNER mode: returns `VendorConfigDescriptor(vendor, "PARTNER", Map.of())`. For CUSTOM mode: returns `VendorConfigDescriptor(vendor, "CUSTOM", Map.of("ref", "vendor-credentials:{vendorConfigId}"))` — payment-core will not resolve this; it instead invokes a new adapter-layer port (out of scope for 7b, defined in `adapters` Wave A; if not present, define `CredentialsResolver` in `provisioning/usecase/CredentialsResolver.java` and impl it via `EncryptionService.decrypt(...)` on demand — the impl lives in 7b and is called by payment-core in 8b).
   - **The actual decryption happens server-side, never crosses a module boundary as plaintext.** Decrypted credentials flow only from `CredentialsResolver` → `payment-core`'s adapter dispatch (which then forwards to the adapter's `initiate(...)` call — adapters never see the encrypted form).
   - TTL = `300s`.

3. **`CredentialsResolver`** interface + impl (`pay.conflux.backend.provisioning.usecase`):
   - Interface: `Map<String, String> resolveCredentials(UUID businessId, String vendor)`.
   - Impl (`CredentialsResolverImpl`): looks up `VendorConfig`, decrypts `credentialsEncrypted` with purpose `"vendor-credentials"`, returns the map. PARTNER mode returns the platform credentials from config (`conflux.adapters.partner-credentials.{vendor}.*` — read via `@ConfigurationProperties("conflux.adapters")` into a new `PartnerCredentialsConfig` record; missing config for a vendor → `IllegalStateException` at startup, not runtime, via `@PostConstruct` validation only against vendors that have at least one PARTNER-mode `VendorConfig` row at boot — or defer the validation to first call if startup-time DB read is undesired).
   - **Never logs the decrypted map.**

4. **Modulith event listeners** (`pay.conflux.backend.provisioning.eventlistener`):
   - `MerchantVerifiedEventListener`:
     - `@ApplicationModuleListener` on `MerchantVerifiedEvent`.
     - Calls `CreateBusinessUseCase.execute(merchantId, new CreateBusinessRequest("Default Business", "Default Business", null))` to auto-provision the merchant's first business.
     - Idempotent: if the merchant already has a business, skip. (Use `BusinessRepository.existsByMerchantIdAndDeletedFalse`.)
   - `UserBlockedEventListener`:
     - `@ApplicationModuleListener` on `UserBlockedEvent`.
     - For each business owned by the user: set `Business.status = INACTIVE` and evict all `apikey:*` and `vendorconfig:{businessId}:*` cache entries.
     - Use `StringRedisTemplate.delete(...)` for cache eviction; use a scan-based approach to find matching keys (avoid full-keyspace SCAN on hot Redis — keep a secondary index `provisioning:business:{businessId}:apikeys` set, populated on cache write, scanned on eviction).

5. **Cache write-through path** — `GetBusinessByApiKeyUseCaseImpl` adds the new entry to the secondary index `provisioning:business:{businessId}:apikeys` on every cache write. `UserBlockedEventListener` reads that set, deletes each member, then deletes the set itself.

TESTS (cumulative target: 70% module coverage)

Unit:
- `GetBusinessByApiKeyUseCaseImpl` — cache hit, cache miss + DB hit, cache miss + DB miss (UnauthorizedException), INACTIVE business (UnauthorizedException).
- `GetVendorConfigUseCaseImpl` — PARTNER mode returns empty refs, CUSTOM mode returns opaque ref, missing config throws.
- `CredentialsResolverImpl` — PARTNER returns platform creds, CUSTOM decrypts.
- `MerchantVerifiedEventListener` — auto-creates business on first event; second event with same merchantId is a no-op.
- `UserBlockedEventListener` — sets business INACTIVE, evicts both apikey and vendorconfig caches via the secondary index.

Integration (Testcontainers Postgres + Redis):
- End-to-end: register merchant in identity (via REST or repository seed) → KYC verify → assert default business exists in provisioning + a cache entry is NOT pre-populated. Then GenerateApiKey → ValidateApiKey hot path (2nd call hits cache). Block user → 3rd ValidateApiKey returns Unauthorized.
- `ApplicationModules.verify()` green (modulith doc regenerated).
- `@RecordApplicationEvents` test confirms exactly one `MerchantVerifiedEvent` triggers exactly one `CreateBusinessUseCase` invocation.

ACCEPTANCE CRITERIA (this sub-prompt)
- All 7a criteria still hold.
- JaCoCo ≥ 70%.
- p95 latency for `GetBusinessByApiKeyUseCaseImpl` < 50 ms (microbenchmark with warm cache — 1k calls, assert).
- Modulith doc regenerated and `provisioning` module shows the new listeners + impls with no leakage.
- Commit: `feat(provisioning): cross-module impls + Modulith listeners + Redis cache (7b)`.

FORBIDDEN
- Modifying the locked `GetBusinessByApiKeyUseCase` / `GetVendorConfigUseCase` interfaces or their DTOs.
- Logging plaintext credentials, API keys, or webhook secrets.
- Implementing controllers (7c).
- Implementing the webhook PING dispatcher (8b's responsibility).
- Touching identity, ledger, risk, quota, adapters, payment-core.

Output: cache-hit trace from a real test run, modulith doc snippet showing the listeners, JaCoCo tail, latency histogram tail.
```

---

## Prompt 7c — provisioning controllers + coverage push + jqwik invariants

```
You are completing the `conflux-provisioning` Wave B module on branch `phase-1/provisioning`. Sub-prompts 7a and 7b are committed. Your sub-scope (7c): the public merchant + admin REST controllers, OpenAPI tags, route constants, jqwik property tests for API-key invariants, and the final coverage push to ≥ 80%.

READ FIRST
- The 7a + 7b commits
- DOCS/features/provisioning/PRD.md §3 (user stories — derive controller endpoints from these)
- Existing Wave A `*Routes` classes for naming convention reference

DELIVERABLES

1. **`ProvisioningRoutes`** (`pay.conflux.backend.provisioning.constant.ProvisioningRoutes`):
   - `MERCHANT_BUSINESSES = "/api/v1/merchant/businesses"`
   - `MERCHANT_BUSINESS_BY_ID = "/api/v1/merchant/businesses/{id}"`
   - `MERCHANT_BUSINESS_VENDORS = "/api/v1/merchant/businesses/{id}/vendors"`
   - `MERCHANT_BUSINESS_APIKEYS = "/api/v1/merchant/businesses/{id}/api-keys"`
   - `MERCHANT_BUSINESS_APIKEY_BY_ID = "/api/v1/merchant/businesses/{id}/api-keys/{keyId}"`
   - `MERCHANT_BUSINESS_WEBHOOK = "/api/v1/merchant/businesses/{id}/webhook"`
   - `ADMIN_BUSINESSES = "/api/v1/admin/businesses"`
   - `ADMIN_BUSINESS_BY_ID = "/api/v1/admin/businesses/{id}"`

2. **Controllers (port + adapter pattern)**:
   - `MerchantBusinessController` (interface, mapping annotations) + `MerchantBusinessControllerImpl` (impl, `@PreAuthorize`):
     - `POST /merchant/businesses` → `CreateBusinessUseCase`. Auth: `hasAuthority('MERCHANT')`. Returns `ApiResult.created(BusinessDto)`.
     - `GET /merchant/businesses` → list businesses owned by the authenticated merchant. Returns `ApiResult.ok(List<BusinessSummaryDto>)`.
     - `GET /merchant/businesses/{id}` → returns `BusinessDto`. 403 if business.merchantId ≠ authenticated merchant.
     - `POST /merchant/businesses/{id}/vendors` → `ConfigureVendorUseCase`. Tenant-isolation check on `{id}`.
     - `POST /merchant/businesses/{id}/api-keys` → `GenerateApiKeyUseCase`. Returns plaintext key once. Tenant check.
     - `POST /merchant/businesses/{id}/api-keys/{keyId}/rotate` → revokes the old key, generates a new one, returns new plaintext. Tenant check.
     - `DELETE /merchant/businesses/{id}/api-keys/{keyId}` → revoke (soft). Tenant check.
     - `PUT /merchant/businesses/{id}/webhook` → `UpdateWebhookUseCase`. Tenant check.
   - `AdminBusinessController` + impl: read-only admin views. `@PreAuthorize("hasAuthority('ADMIN_MANAGER')")`. `GET /admin/businesses` (paginated, spec-based search), `GET /admin/businesses/{id}`, `POST /admin/businesses/{id}/deactivate`, `POST /admin/businesses/{id}/activate`.
   - **Tenant-isolation helper:** define a single `BusinessOwnershipGuard` component that throws `ForbiddenException` if `business.merchantId != currentUserId`. Inject and call at the top of every merchant-controller method that takes a path-variable business id.

3. **OpenAPI**: add `@Tag(name = "Provisioning - Merchant")` / `@Tag(name = "Provisioning - Admin")` on the interfaces. DO NOT commit changes to `DOCS/contracts/openapi.json` — Wave B acceptance gate regenerates it.

4. **jqwik property tests** (`conflux-provisioning/src/test/java/pay/conflux/backend/provisioning/property/`):
   - `ApiKeyGenerationProperty`:
     - For 10k random `(businessId, environment)` pairs, generate a key → hash it → assert the hash collides for **exactly zero** pairs in the sample (`Set<String>` of size N for N generations).
     - For each generated key, `ValidateApiKey(plaintext)` returns the same `(businessId, environment)` tuple. Property: validate-after-generate is identity.
   - `ApiKeyRevocationProperty`:
     - After `revoke()`, `ValidateApiKey(plaintext)` throws `UnauthorizedException` for that key — exhaustive over the random sample.
   - `WebhookUrlValidationProperty`:
     - All `http://...` URLs are rejected; all `https://...` URLs are accepted at the validator layer (drive via the request DTO).

5. **Coverage push to ≥ 80%**:
   - Add controller slice tests (`@WebMvcTest`) covering: every endpoint, the auth matrix (no auth → 401; wrong authority → 403; correct authority → 2xx), validation rejection (`@Valid` failure → 400 with `VALIDATION_ERROR`).
   - Edge cases: rotating a key for a business in INACTIVE status → 400 `INVALID_OPERATION_STATE`. LIVE key for a non-ACTIVE merchant → 400 `INVALID_OPERATION_STATE`.

TESTS — see deliverables 4 and 5.

ACCEPTANCE CRITERIA (this sub-prompt — final for the module)
- All 7a + 7b criteria still hold.
- JaCoCo ≥ 80%.
- Every controller endpoint has at least one slice test for the happy path and one for the auth-rejection path.
- jqwik samples ≥ 500 each (10k for the no-collision invariant).
- ArchUnit + Modulith green.
- gitleaks, Spotless, PMD clean.
- Mapper-audit grep still empty (no DTO leaks encrypted/hash fields).
- Commit: `feat(provisioning): controllers + jqwik invariants + coverage push (7c) — closes provisioning`.

FORBIDDEN
- Modifying any locked Wave A contract.
- Editing `DOCS/contracts/openapi.json`.
- Returning plaintext API keys outside the generate / rotate response.
- Returning decrypted credentials in any response.
- Field injection.
- `@Data` on entities.

Output: file tree, controller slice test results, jqwik trial counts, final JaCoCo per-class table.
```
