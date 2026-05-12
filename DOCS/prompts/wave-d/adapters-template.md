# Phase 1 Wave D — Track 2: remaining adapters (template)

> **Branch:** one parallel branch per vendor — `phase-1/adapter-nagad`, `phase-1/adapter-rocket`, `phase-1/adapter-upay`, `phase-1/adapter-pathao`, `phase-1/adapter-mcash`, `phase-1/adapter-stripe`. Each agent runs the prompt below with the vendor-specific deltas from the matching section.
>
> **Scope:** instantiate a real `PaymentProvider` adapter for one of the six remaining vendors (NAGAD, ROCKET, UPAY, PATHAO, MCASH, STRIPE). The `Vendor` enum already contains all six values from Wave A — **no enum extension is required**, and **no sub-prompt-0-equivalent runs** in Wave D Track 2.
>
> **Read first (every adapter agent):** [Wave D index](../PHASE_1_WAVE_D_PROMPTS.md) — cross-cutting decisions; `DOCS/prompts/wave-c/bkash.md` + `wave-c/sslcommerz.md` — canonical templates; `conflux-adapters/CLAUDE.md`; the vendor-specific delta block at the bottom of THIS file.

---

## Generic adapter prompt (instantiate per vendor)

```
You are running one of six PARALLEL Wave D Track 2 adapter sub-prompts on branch
`phase-1/adapter-{vendor}` (replace `{vendor}` with one of `nagad`, `rocket`,
`upay`, `pathao`, `mcash`, `stripe`). The Wave C adapter prompts
(`DOCS/prompts/wave-c/bkash.md` and `DOCS/prompts/wave-c/sslcommerz.md`) are
your canonical template. You implement ONE adapter and its tests. Do NOT
implement any other vendor; their branches run in parallel.

READ FIRST
- DOCS/prompts/PHASE_1_WAVE_D_PROMPTS.md (full — cross-cutting decisions)
- DOCS/prompts/wave-c/bkash.md OR wave-c/sslcommerz.md — pick whichever is closer to your vendor's auth flavor:
  - **Token-based vendors** (NAGAD, ROCKET, UPAY, PATHAO, MCASH, STRIPE): bkash.md is the closer template (it uses `VendorAuthClient` + `TokenService` for session tokens).
  - **Stateless / per-request credentials**: sslcommerz.md is the closer template (no token; creds passed on every call).
- conflux-adapters/CLAUDE.md
- DOCS/features/adapters/PRD.md + TECH_SPEC.md
- DOCS/features/adapters/TECH_SPEC.md § specific to your vendor if it exists (otherwise the generic adapter contract)
- The vendor's official integration documentation. Use Context7 MCP first; fall back to the vendor's developer portal. The "vendor-specific deltas" section at the bottom of `adapters-template.md` lists known reference URLs.

WORK ONLY IN
- conflux-adapters/src/main/java/pay/conflux/backend/adapters/{vendor}/* (new package — `Bkash` and `Sslcommerz` are already adjacent siblings; mirror their package structure exactly).
- conflux-adapters/src/test/java/pay/conflux/backend/adapters/{vendor}/*
- conflux-application/src/main/resources/application.yml (add the `{vendor}` block under `conflux.adapters.partner-credentials.*` per the credential shape your vendor needs).
- conflux-application/src/main/resources/application-test.yml (mirror with blank/test fixtures).
- conflux-application/src/test/java/pay/conflux/backend/adapters/{vendor}/* (WireMock contract test lives in conflux-application, per Wave C precedent).

DO NOT TOUCH
- `Vendor` enum — all six values already exist from Wave A.
- `PaymentProvider` port, `VendorAuthClient`, `TokenService`, `VendorCredentials`, `VendorResponse`, `VendorStatus`, `ErrorCode` — all locked.
- Any other vendor's package (Bkash, Sslcommerz, Mock are off-limits).
- `ResilienceConfig` — the circuit breaker registry loops `Vendor.values()` and auto-seeds your vendor.
- `PartnerCredentialsConfig` — `Map<String, Map<String, String>>` absorbs your new YAML block via Spring relaxed binding.
- `conflux-payment-core` — adapter changes never reach into payment orchestration.
- Any Flyway migration.
- Root `pom.xml`.

DELIVERABLES (mirror Wave C bkash.md or sslcommerz.md sections of the same name; the deltas appear in the vendor-specific block at the bottom of this file)

1. **`{Vendor}AuthClient`** (only if your vendor needs session tokens — skip for stateless vendors).
2. **`{Vendor}ErrorMapper`** — maps vendor-specific error codes to the locked `ErrorCode` enum. Property-tested with jqwik for exhaustiveness over the documented error list.
3. **`{Vendor}Adapter`** implementing `PaymentProvider`:
   - `Vendor supports()` returns `Vendor.{VENDOR}`.
   - `initiate(InitiatePaymentCommand, VendorCredentials) → VendorResponse`
   - `queryStatus(String vendorTransactionId, VendorCredentials) → VendorStatus`
   - `refund(RefundPaymentCommand, VendorCredentials) → VendorResponse`
   - Uses `httpClientFactory.clientFor(Vendor.{VENDOR})` for HTTP (isolation per cross-cutting decision #4 from Wave C).
   - Wraps every call in `AdapterResilience.execute(Vendor.{VENDOR}, ...)` for circuit-breaker.
4. **`{vendor}` YAML block** under `conflux.adapters.partner-credentials:` in `application.yml` (env-var-driven, sandbox URL default) and `application-test.yml` (blank fixtures + sandbox URL). After Spring relaxed binding, the adapter reads creds as `creds.get("camelCaseKey")` — never hyphenated, never UPPER_SNAKE.
5. **Catch-clause enumeration** — only these exceptions are caught and mapped: `IOException`, `JsonProcessingException`, `SocketTimeoutException`, `MfsAdapterException`. Never catch bare `RuntimeException` or `Exception`. Socket timeouts force `VENDOR_DOWN` (Wave C precedent).
6. **Secret-redaction** in code, logs, JavaDocs, commit messages, and trace samples. Mask any vendor session ID / `bank_tran_id` / equivalent to `first4+***`.

TESTS

Unit:
- `{Vendor}ErrorMapperTest` — jqwik property test asserting every documented error code from the vendor's spec maps to a non-`UNKNOWN` `ErrorCode`.
- `{Vendor}AdapterTest` (Mockito-only, no network) — each of the three `PaymentProvider` methods: happy path + one failure mapped via the `{Vendor}ErrorMapper`.
- `{Vendor}AuthClientTest` (if applicable) — WireMock-backed (use the existing classpath `wiremock-jetty12 3.10.0`). Asserts the auth POST is shaped per the vendor's spec and that token caching honors the documented TTL.

WireMock contract (in conflux-application):
- `initiate_success_capturesPayment`, `initiate_failure_mapsViaErrorMapper`, `queryStatus_terminalStates_*`, `refund_success_*`, `refund_idempotency_*` (if the vendor specifies idempotency semantics).
- If your vendor has a Wave C-style "duplicate transaction" error code, add a `initiate_duplicateTransaction_mapsToResourceAlreadyExists` test.

ACCEPTANCE CRITERIA
- `mvn -pl conflux-adapters,conflux-application -am verify` BUILD SUCCESS.
- No per-module JaCoCo gate drop.
- ArchUnit + Modulith green.
- gitleaks clean; the log-leak grep (Wave C step 6b heuristics) passes on captured test output.
- Single commit: `feat(adapters): {VENDOR} adapter (wave-d)`.

FORBIDDEN
- Implementing any other vendor.
- Editing `Vendor.java`, `ResilienceConfig`, `PaymentProvider`, or any locked port.
- Adding any `ErrorCode` value.
- Editing prior migrations.
- Adding a root-pom dependency.
- Touching `conflux-payment-core` (the Bkash Execute follow-up has its own sub-prompt — `bkash-execute-wiring.md` — that is NOT this branch).

Output: file tree, the `partner-credentials.{vendor}` YAML block, jqwik mapper exhaustiveness count, WireMock contract test count, JaCoCo tail.
```

---

## Vendor-specific deltas

Each implementing agent replaces `{VENDOR}` and `{vendor}` everywhere and
applies the deltas below. Auth-flavor classification is from the Wave C
acceptance gate's report (filed under "Wave D readiness checklist"). The
implementing agent verifies it against current vendor docs before coding —
flavor classifications drift, and stale prompt content is worse than no prompt
content.

### NAGAD
- **Flavor:** token-based (PGP-encrypted handshake → session ID).
- **Reference:** Nagad Tokenized Checkout integration PDF (request via the
  business onboarding channel; no public dev portal).
- **Closest template:** `wave-c/bkash.md` (has the token + lifecycle steps).
- **Credentials map keys** (camelCase after binding): `merchantId`,
  `merchantPrivateKey` (PEM, multi-line — store in env var with escaped newlines),
  `nagadPublicKey`, `baseUrl`.
- **Known quirks:** the auth handshake encrypts a randomly-generated
  `challenge` with the merchant's RSA private key; the response contains the
  Nagad-issued sessionId. Cache by sessionId, not by merchant. TTL is short
  (~5 min). The `BkashAdapter.confirm(...)` pattern (Wave C Execute step) is
  **not** required here — Nagad's flow is `checkout/initialize → user redirects
  → callback → server queries status` (no separate Execute call).
- **Error catalog:** Nagad's error codes are documented in the integration PDF.
  Property-test the mapper for exhaustiveness over the documented list.

### ROCKET (Dutch-Bangla MFS)
- **Flavor:** per-request signed form-body (no session token).
- **Reference:** Dutch-Bangla Rocket merchant API spec (private; obtain via
  business onboarding).
- **Closest template:** `wave-c/sslcommerz.md` (per-request creds, no token).
- **Credentials map keys:** `merchantId`, `merchantPin`, `secretKey`, `baseUrl`.
- **Known quirks:** every request is signed with HMAC-SHA1 over a sorted
  param string. Use `common.crypto.HmacSigner` — do NOT roll your own.
  Rocket's status query is synchronous; no polling needed unless the response
  is `PENDING`.

### UPAY (UCB MFS)
- **Flavor:** token-based with very short TTL (~15 min).
- **Reference:** UCB Upay merchant integration manual (private).
- **Closest template:** `wave-c/bkash.md`.
- **Credentials map keys:** `merchantNumber`, `merchantPassword`, `apiKey`,
  `baseUrl`.
- **Known quirks:** the token endpoint returns a `Bearer` token that goes in
  the `Authorization` header verbatim (no extra envelope). 401 from any call
  means token is expired — refresh and retry exactly once before mapping to
  `MFS_ADAPTER_FAILURE`. Refunds are NOT supported via API at the time of
  Wave C reference docs — confirm against the current spec; if still
  unsupported, throw `UnsupportedOperationException("Upay refund not yet
  supported via API — manual reconciliation only")` and map it to a clear
  `ErrorCode.VALIDATION_ERROR` at the controller layer. Document this in the
  adapter's Javadoc.

### PATHAO PAY
- **Flavor:** OAuth2 client-credentials (token-based, ~1 h TTL).
- **Reference:** https://developer.pathao.com (newer, partially public).
- **Closest template:** `wave-c/bkash.md` with the standard OAuth2 client-credentials grant on the auth client.
- **Credentials map keys:** `clientId`, `clientSecret`, `baseUrl`,
  `scope` (default `"payment:write payment:read"`).
- **Known quirks:** OAuth2 errors are returned as RFC6749-shaped JSON
  (`{"error": "invalid_grant", ...}`). Map per the RFC plus Pathao-specific
  `"payment_failed"` and `"insufficient_funds"` subtypes. Idempotency-Key
  header is supported and SHOULD be sent for `initiate` (use the existing
  `IdempotencyKey` from `payment-core`).

### MCASH
- **Flavor:** per-request basic-auth (no token).
- **Reference:** Mcash merchant docs (very limited; integration via direct support).
- **Closest template:** `wave-c/sslcommerz.md`.
- **Credentials map keys:** `merchantId`, `password`, `baseUrl`.
- **Known quirks:** the API is XML-over-HTTP, not JSON. Use Jackson's
  `XmlMapper` (already on the Spring Boot classpath via `jackson-dataformat-xml`
  — verify presence; if missing, this is the ONE allowed module-pom dep
  addition for this adapter and must be flagged in the commit body). Status
  query is poll-based with a 5-second backoff recommendation.

### STRIPE
- **Flavor:** API-key auth (long-lived bearer key per environment).
- **Reference:** https://stripe.com/docs/api (public, current).
- **Closest template:** `wave-c/sslcommerz.md` (no token rotation), but with
  Stripe's idiomatic SDK consumption (see "Library choice" below).
- **Credentials map keys:** `secretKey` (`sk_test_...` or `sk_live_...`),
  `webhookSecret` (for callback verification — used by payment-core, not this
  adapter directly), `baseUrl` (always `https://api.stripe.com/v1`).
- **Library choice:** the official `com.stripe:stripe-java` SDK is on the
  vendor's allowed-list. If adding it, add to root pom `<dependencyManagement>`
  (this is permitted as a **second** Wave D dep addition, after Track 1's jjwt
  — flag the dep in the commit body and reference cross-cutting decision #3 as
  the exception). Alternatively, hand-roll HTTP via the existing
  `HttpClientFactory.clientFor(Vendor.STRIPE)` (mirrors all other adapters,
  but you lose Stripe's auto-retry + idempotency-key plumbing). Pick the SDK
  unless there's a specific reason not to; document the choice in the
  adapter's package-info.
- **Known quirks:** Stripe's PaymentIntent lifecycle has a similar two-step
  shape to Bkash's tokenized checkout (`create → confirm`), but the SDK
  handles confirmation server-side for synchronous flows. For
  Bangladeshi-card-on-Stripe scenarios, 3DS authentication redirects out and
  back via `next_action.redirect_to_url`. Treat this the same way the Bkash
  Execute follow-up is being handled in `bkash-execute-wiring.md` —
  the adapter exposes `confirm(paymentIntentId, creds)` and payment-core wires
  it on callback. File this as a Wave D Track 2 follow-up if not completed
  inline.
- **Currency note:** Stripe's amount field is in the smallest currency unit
  (cents/paise/etc.). The locked `Money` representation in `common` uses
  `BigDecimal` with 4 decimal places — multiply by 10000 / currency-scaling
  factor on outbound, divide on inbound. Property-test the round-trip.

---

## What the implementing agent must explicitly resolve

Vendor APIs drift faster than this template can. Before coding, the agent
MUST do a 15-minute vendor-doc spike and confirm:

1. The current authentication flavor matches the delta block (token-based vs
   per-request vs OAuth2).
2. The current error-code list and any new codes added since Wave C reference
   was written.
3. Whether the vendor has shipped a 2-step Execute / Confirm flow that the
   adapter's `initiate` cannot satisfy alone (file as a Wave D Track 2
   follow-up, do NOT scope-creep into `payment-core`).
4. Whether refunds are supported via API at all.
5. Idempotency-key support (mandatory? optional? unsupported?).

Document these five answers as a one-paragraph "vendor-doc spike" entry in the
commit body. Future Wave E reviewers read this to understand drift.
