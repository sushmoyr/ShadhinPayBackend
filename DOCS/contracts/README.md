# API Contracts

This directory holds **frozen API contracts** consumed by downstream teams (frontend, mobile,
partner integrations) before the corresponding backend endpoints land.

## `openapi.json`

The Phase 0 frozen reference of the ShadhinPay backend OpenAPI document. The frontend agent
uses this file as its **single source of truth** for request/response types and routing until
Phase 1 Wave B introduces the first real controllers.

### How it is generated

`shadhinpay-application` exposes an opt-in Maven profile that boots the Spring Boot application
under the `openapi` Spring profile (H2 in-memory, Flyway off, permissive security) and lets
`springdoc-openapi-maven-plugin` scrape `/v3/api-docs`:

```bash
mvn -pl shadhinpay-application -am -Popenapi -DskipTests=true verify
```

The emitted file lands at `shadhinpay-application/target/openapi/openapi.json`. To refresh the
frozen reference, copy that file into this directory (preserving the `x-shadhinpay-*` header
extensions and a stable `servers[0].url`).

### Breaking-change policy

Any backend PR that **alters the schema** of `openapi.json` (removes a field, changes a type,
renames a path, tightens a constraint) MUST carry the `breaking-change` GitHub label. The label
triggers the frontend agent to regenerate its types in the same merge train, per
`DEVELOPMENT_WORKFLOW.md` §9.

Additive, backwards-compatible changes (new endpoints, new optional fields, new tags) do **not**
require the label but the frontend agent should still be notified in the PR description.

### What the Phase 0 contract contains

- `info` — title, version (`v1`), contact, proprietary license
- `security` — global `ApiKeyAuth` requirement (HTTP bearer, `Authorization` header)
- `components.securitySchemes.ApiKeyAuth` — placeholder bearer-token scheme; the real tenant
  API-key middleware lands in Phase 1 Wave B
- `paths` — empty (Phase 0 ships infrastructure only; no controllers)

### Validation

The frontend agent should validate any consumed copy with a standard OpenAPI 3.0.x validator
(`swagger-cli validate`, `openapi-typescript`, etc.). The `x-shadhinpay-*` extension fields
are intentional; they document provenance and are ignored by spec-compliant tooling.
