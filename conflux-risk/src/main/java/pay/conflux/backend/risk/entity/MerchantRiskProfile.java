package pay.conflux.backend.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.risk.enums.TrustLevel;

@Entity
@Table(name = "merchant_risk_profiles")
@Getter
@Setter
public class MerchantRiskProfile extends Auditable {

  @Id
  @Column(name = "merchant_id")
  private UUID merchantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_level", nullable = false, length = 16)
  private TrustLevel trustLevel = TrustLevel.NEW;

  @Column(name = "custom_limits", columnDefinition = "TEXT")
  private String customLimits;
}
