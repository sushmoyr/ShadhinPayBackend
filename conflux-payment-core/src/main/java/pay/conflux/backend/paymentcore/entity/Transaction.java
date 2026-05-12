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
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
 * The orchestrator aggregate. Every {@code POST /api/v1/payments} either creates exactly one row
 * here or replays the row pointed to by a matching {@code IdempotencyRecord}.
 *
 * <p>Concurrency is protected by the {@code @Version} column — the reconciliation poller in 8b is
 * the only path that may finalize a {@code PENDING_RECOVERY} row, and the optimistic-lock retry
 * lives in {@code ProcessVendorCallbackUseCase} (also 8b).
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
      @Index(name = "idx_transactions_business_created", columnList = "business_id, created_at"),
      @Index(name = "idx_transactions_vendor_trx_id", columnList = "vendor_transaction_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "amount_value", nullable = false, precision = 19, scale = 4)
  private BigDecimal amountValue;

  @Column(name = "amount_currency", nullable = false, length = 3)
  private String amountCurrency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TransactionStatus status;

  @Column(name = "vendor", nullable = false)
  private String vendor;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false)
  private TransactionMode mode;

  @Column(name = "merchant_order_reference", nullable = false)
  private String merchantOrderReference;

  @Column(name = "vendor_transaction_id")
  private String vendorTransactionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, String> metadata;

  @Column(name = "callback_url")
  private String callbackUrl;

  @Column(name = "webhook_url")
  private String webhookUrl;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Version
  @Column(name = "version", nullable = false)
  private long version;
}
