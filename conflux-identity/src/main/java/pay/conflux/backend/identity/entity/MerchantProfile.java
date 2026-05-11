package pay.conflux.backend.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.identity.entity.converter.KycDataConverter;
import pay.conflux.backend.identity.enums.OnboardingStatus;

@Getter
@Setter
@Entity
@Table(name = "merchant_profiles")
public class MerchantProfile extends Auditable {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "onboarding_status", nullable = false, length = 32)
  private OnboardingStatus onboardingStatus;

  @Convert(converter = KycDataConverter.class)
  @Column(name = "kyc_data")
  private String kycData;
}
