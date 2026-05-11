# Tech Spec - MFS Adapter Library (conflux-adapters)

## 1. Architecture
This module defines the **Outbound Port** (`PaymentProvider`) and its multiple **Adapter** implementations. It is a set of "Strategy" implementations.

## 2. The Core Port (Interface)

```java
public interface PaymentProvider {
    VendorResponse initiate(PaymentRequest request, VendorCredentials creds);
    VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds);
    VendorResponse refund(RefundRequest request, VendorCredentials creds);
    boolean supports(Vendor vendor);
}
```

### 2.1 Unified Response Model
```java
public record VendorResponse(
    VendorStatus status,
    String vendorTrxId,
    String redirectUrl,
    String rawResponse,
    ErrorCode errorCode
) {}
```

## 3. Implementation Details

### 3.1 Isolated HTTP Clients
Each implementation (e.g., `BkashAdapter`, `StripeAdapter`) must instantiate its own `OkHttpClient` with specific timeouts:
*   **Connection Timeout:** 5s.
*   **Read/Write Timeout:** 10s.

### 3.2 Centralized Token Service
A shared component in the `conflux-adapters` module:
*   `TokenService.getToken(vendor, credentials)`: Checks Redis for a valid token. If missing or expired, calls the vendor's Auth API and caches the result.

### 3.3 Error Mapping Logic
Each adapter must contain an internal `ErrorMapper`:
```java
public class BkashErrorMapper {
    public static ErrorCode map(String vendorCode) {
        return switch (vendorCode) {
            case "2023" -> ErrorCode.INSUFFICIENT_FUNDS;
            case "503" -> ErrorCode.VENDOR_DOWN;
            default -> ErrorCode.MFS_ADAPTER_FAILURE;
        };
    }
}
```

## 4. Specific Adapters

### 4.1 `BkashAdapter`
*   **Auth:** Requires `app_key` and `app_secret` to get a `grantToken`.
*   **Flow:** Returns an `id_token` and a `bkashURL`.

### 4.2 `StripeAdapter`
*   **Auth:** Bearer Token (Secret Key).
*   **Flow:** Returns a `Session` URL.

### 4.3 `MockAdapter`
*   **Purpose:** Development and Sandbox.
*   **Behavior:** Simulates success/fail/cancel based on the `amount` or a specific metadata flag.

## 5. Transaction Reconciliation (Polling Job)
A background job in the `payment-core` (or a dedicated `settlement` module) will:
1.  Find `PENDING` transactions created > 5 mins ago.
2.  Lookup the appropriate `PaymentProvider` using the `Strategy` pattern.
3.  Call `queryStatus`.
4.  Update the transaction state and notify the Ledger if successful.

## 6. Testing Strategy
*   **Contract Tests:** Use **WireMock** to simulate vendor API responses (Success, Error, Timeout) and verify the adapter correctly maps them to `VendorResponse`.
*   **Retry Test:** Verify that `TokenService` correctly retries authentication if the vendor returns a 401.
*   **Isolation Test:** Verify that a slow response from the `bKashAdapter` does not block the `NagadAdapter`.
