# Tech Spec - Common Shared Library (shadhinpay-common)

## 1. Purpose
The `shadhinpay-common` module provides the foundational classes, utilities, and contracts shared across all Maven modules in the ShadhinPay monolith. It ensures consistency in API responses, error handling, and data persistence patterns.

## 2. API Response Envelope
All modules must return the `ApiResult<T>` envelope for REST endpoints.

### 2.1 `ApiResult<T>`
```java
public record ApiResult<T>(
    T data,
    ApiResultMeta meta,
    PaginationInfo pagination
) {
    public static <T> ApiResult<T> ok(T data) { ... }
    public static <T> ApiResult<T> ok(T data, PaginationInfo pg) { ... }
    public static ApiResult<Void> error(String message, ErrorCode code) { ... }
}
```

### 2.2 `ErrorCode` (Global Registry)
A centralized enum to prevent duplicate error definitions.
*   `VALIDATION_ERROR`
*   `UNAUTHORIZED`
*   `RESOURCE_NOT_FOUND`
*   `INSUFFICIENT_FUNDS`
*   `IDEMPOTENCY_CONFLICT`
*   `MFS_ADAPTER_FAILURE`
*   `QUOTA_EXCEEDED`

## 3. Persistence Foundations

### 3.1 Base Entities
*   `Auditable`: Includes `@CreationTimestamp` and `@UpdateTimestamp`.
*   `AuditableAndSoftDeletable`: Extends `Auditable` with a `boolean deleted = false` field.
    *   **Note:** As per project guidelines, soft deletion is handled via **Manual Filter (Explicit)** in repositories (e.g., `AndDeletedFalse`).

### 3.2 Monetary Representation
*   All financial fields must use `java.math.BigDecimal`.
*   Database precision: `Decimal(19, 4)`.
*   Rounding Mode: `HALF_EVEN` (Banker's Rounding).

## 4. Exception Hierarchy
*   `shadhinpay-common` defines the `BaseException` (Runtime) which includes `ErrorCode` and `HttpStatus`.
*   Global `ExceptionTranslator` (Spring `@ControllerAdvice`) handles these exceptions and converts them to `ApiResult.error()`.

## 5. Inter-Module Event Delivery (Spring Modulith)
To maintain consistency between modules (e.g., Payment → Ledger), inter-module communication uses **Spring Modulith events** backed by the **JDBC Event Publication Registry**. This is Modulith's built-in transactional outbox — we do **not** define a separate generic `OutboxEvent` entity in `common`.

### 5.1 Event Publication Registry
*   **Dependency:** `spring-modulith-events-jdbc` (registry persistence) + `spring-modulith-events-api`.
*   **Storage:** Modulith manages its own `event_publication` table that records every event published from a `@TransactionalEventListener` boundary, along with a per-listener completion marker.
*   **Guarantee:** At-least-once delivery. If a listener throws (or the JVM dies before completion), the publication remains incomplete and is replayed on the next application start via `IncompleteEventPublications.resubmitIncompletePublications(...)`.
*   **Idempotency:** Listeners must be idempotent. The `ledger` module uses `(sourceType, sourceId)` uniqueness; other consumers use the event's natural key.

### 5.2 Publishing Pattern
Events are published from a use case **inside** the originating database transaction:
```java
eventPublisher.publishEvent(new PaymentCompletedEvent(...));
```
Consumers must use `@TransactionalEventListener(phase = AFTER_COMMIT)` so they only run after the source transaction commits. Combine with `@Async("asyncEventExecutor")` to avoid blocking the publishing thread.

### 5.3 What is NOT covered by this registry
*   **Outbound webhooks to merchants** — these are external HTTP deliveries with their own retry semantics and live in `payment-core` as `WebhookOutbox` (a separate concern).
*   **Synchronous pre-flight checks** (risk, quota) — these are direct use-case calls, not events.

## 6. Route Constants
The `Routes` class centralizes all path mappings to avoid magic strings in `@RequestMapping`.
```java
public final class Routes {
    public static final String V1_PREFIX = "/api/v1";
    // Nested classes for sub-paths...
}
```

## 7. Security & Multi-Tenancy
### 7.1 Passive Tenant Isolation
- **Hibernate Filters:** Every tenant-specific entity must use a `@FilterDef` and `@Filter(name = "tenantFilter", condition = "business_id = :businessId")`.
- **Enforcement:** A global `TenantInterceptor` or Aspect must enable this filter using the `businessId` from the `SecurityContext` for every Hibernate session. This prevents accidental cross-tenant data leaks.

### 7.2 Security Context Utilities
A `SecurityUtils` helper to retrieve the `currentMerchantId()`, `currentBusinessId()`, or `currentAdminId()` from the Spring Security context.

## 8. Transport & Data Integrity
### 8.1 Transport Security (TLS)
- **Mandatory HTTPS:** All API communication must be encrypted via **TLS 1.2+** (TLS 1.3 preferred).
- **HSTS:** Strict-Transport-Security headers must be enforced to prevent downgrade attacks.

### 8.2 Webhook Integrity (HMAC)
- All outgoing webhook notifications must include a `X-ShadhinPay-Signature` header.
- **Algorithm:** HMAC-SHA256.
- **Payload:** The signature is calculated over the raw JSON request body using the merchant's `webhookSecret`.
- **Implementation:** Shared utility in `common` for generating and verifying these signatures.

## 9. Observability & Traceability
### 9.1 Global Correlation ID
- **X-ShadhinPay-Trace-ID:** Every incoming request must be assigned a unique UUID.
- **Propagation:** This ID must be stored in the SLF4J **MDC (Mapped Diagnostic Context)**.
- **Internal:** Must be included in all Spring Modulith event payloads.
- **External:** Must be sent as a header to all MFS vendors and returned in the final API response for debugging.
