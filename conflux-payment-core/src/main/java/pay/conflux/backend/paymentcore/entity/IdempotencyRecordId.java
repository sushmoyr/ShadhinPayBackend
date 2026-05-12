package pay.conflux.backend.paymentcore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary-key holder for {@link IdempotencyRecord}. Idempotency keys are scoped to the
 * owning {@code business_id} (see Wave B cross-cutting decision #5) — one merchant may own many
 * businesses and we must not let an idempotency-key collision cross that boundary.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "request_key", nullable = false)
  private String requestKey;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdempotencyRecordId that)) {
      return false;
    }
    return Objects.equals(businessId, that.businessId)
        && Objects.equals(requestKey, that.requestKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(businessId, requestKey);
  }
}
