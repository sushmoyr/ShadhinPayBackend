# Tech Spec - Risk & Fraud Engine (shadhinpay-risk)

## 1. Architecture
Follows Hexagonal Architecture. This module provides a synchronous **Decision Service** to the `payment-core` and an asynchronous **Case Management** workflow for Admins.

## 2. Domain Model & Schema

### 2.1 Entities

#### `RiskRule`
*   `id`: UUID
*   `name`: String
*   `expression`: String (SpEL or Drools DSL)
*   `scoreWeight`: Integer (How much this rule contributes to the total score)
*   `action`: Enum (`ALLOW`, `FLAG`, `BLOCK`)
*   `isActive`: Boolean

#### `BlacklistEntry`
*   `id`: UUID
*   `type`: Enum (`PHONE`, `EMAIL`, `IP`, `MERCHANT`)
*   `value`: String (Indexed)
*   `reason`: String
*   `expiresAt`: Instant (Nullable for permanent blocks)

#### `MerchantRiskProfile`
*   `merchantId`: UUID (Unique)
*   `trustLevel`: Enum (`NEW`, `VERIFIED`, `TRUSTED`, `VIP`)
*   `customLimits`: JSONB (Overrides for velocity/volume)

#### `RiskEvaluation` (Audit Log)
*   `id`: UUID
*   `transactionId`: UUID
*   `totalScore`: Integer
*   `decision`: Enum (`ALLOW`, `FLAG`, `BLOCK`)
*   `triggeredRuleIds`: List<UUID>

## 3. API & Ports

### 3.1 Inbound Ports (Use Cases)
*   **`EvaluateTransactionUseCase`**:
    *   Input: `TransactionContext` (Merchant details, Amount, Customer ID, IP).
    *   Logic: Runs all `active` rules through the `RuleEngine`, checks Blacklists, and calculates the `totalScore`.
    *   Output: `RiskDecision` (Action + Score).
*   **`ReviewRiskCaseUseCase`**: Allows Admins to approve/reject a `FLAGGED` transaction.
*   **`UpdateRulesUseCase`**: CRUD for dynamic rules.

### 3.2 Outbound Ports (Adapters)
*   **`CounterCachePort`**: Interface for Redis to track velocities (e.g., `incrWithExpire(key, window)`).
*   **`PersistencePort`**: JPA for rules, blacklists, and audit logs.

## 4. Technical Implementation

### 4.1 SpEL-Based Rule Engine
To achieve the "Dynamic Rule Engine" requirement, we will use **Spring Expression Language (SpEL)**.

```java
public RiskDecision evaluate(TransactionContext ctx) {
    ExpressionParser parser = new SpelExpressionParser();
    EvaluationContext context = new StandardEvaluationContext(ctx);
    
    int totalScore = 0;
    for (RiskRule rule : activeRules) {
        Boolean match = parser.parseExpression(rule.getExpression()).getValue(context, Boolean.class);
        if (Boolean.TRUE.equals(match)) {
            totalScore += rule.getScoreWeight();
            if (rule.getAction() == Action.BLOCK) return RiskDecision.block(rule.getName());
        }
    }
    
    if (totalScore >= FLAG_THRESHOLD) return RiskDecision.flag(totalScore);
    return RiskDecision.allow(totalScore);
}
```

### 4.2 Velocity Tracking (Redis)
We will use Redis keys with TTLs to track window-based activity:
*   **Key Pattern:** `risk:velocity:{merchantId}:{type}:{window}`
*   **Logic:** `INCR` and `EXPIRE`. If `count > limit`, trigger velocity rule.

## 5. Implementation Details

### 5.1 Pre-flight Integration
The `payment-core` will call `EvaluateTransactionUseCase` **synchronously** during the `INITIATED` phase.
*   If `BLOCK` -> Return `403 RISK_REJECTED`.
*   If `FLAG` -> Move transaction to `PENDING_RISK` and notify Admins. Do not dispatch to MFS.

### 5.2 Performance Optimization
*   **Blacklist Caching:** Load active blacklists into a **Redis Set** for $O(1)$ lookups.
*   **Rule Compiling:** Pre-parse and cache SpEL expressions to avoid parsing overhead per request.

## 6. Testing Strategy
*   **Rule Accuracy:** Unit test the SpEL context against multiple `TransactionContext` scenarios (Low amount vs. High amount, Blacklisted phone vs. New phone).
*   **Latency Benchmarking:** Ensure that a suite of 20 rules evaluates in under 10ms.
*   **Race Condition Test:** Verify that velocity counters correctly handle 1,000 requests/sec for the same merchant using Redis `INCR`.
