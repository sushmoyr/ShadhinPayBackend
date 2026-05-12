package pay.conflux.backend.paymentcore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pay.conflux.backend.common.entity.Auditable;

/**
 * Reliable-delivery queue row for outbound merchant webhooks. Inserted in the same DB transaction
 * as the {@code Transaction} status change so the merchant either sees both or neither.
 *
 * <p>The {@code WebhookOutboxDispatcher} (8b) drains rows by polling on {@code (status,
 * next_attempt_at)} — never via inline HTTP from the request thread.
 */
@Entity
@Table(
    name = "webhook_outbox",
    indexes = {
      @Index(name = "idx_webhook_outbox_dispatcher_poll", columnList = "status, next_attempt_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookOutbox extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private WebhookEventType eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private WebhookOutboxStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;
}
