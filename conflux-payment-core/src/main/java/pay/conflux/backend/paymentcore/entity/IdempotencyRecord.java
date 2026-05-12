package pay.conflux.backend.paymentcore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Idempotency replay cache. A successful {@code InitiatePaymentUseCase} call writes one row here in
 * the same DB transaction as the {@code Transaction} insert. Subsequent requests sharing the same
 * {@code (businessId, requestKey)} replay {@link #responsePayload} verbatim and never
 * re-orchestrate.
 *
 * <p>Rows older than {@link #expiresAt} are purged by an hourly cleanup job (8b). This is L2; the
 * L1 cache is Redis with the same TTL.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

  @EmbeddedId private IdempotencyRecordId id;

  @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private Map<String, Object> responsePayload;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
