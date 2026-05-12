package pay.conflux.backend.provisioning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;

@Entity
@Table(
    name = "vendor_configs",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_vendor_configs_business_vendor",
          columnNames = {"business_id", "vendor"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VendorConfig extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Enumerated(EnumType.STRING)
  @Column(name = "vendor", nullable = false)
  private Vendor vendor;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false)
  private VendorMode mode = VendorMode.PARTNER;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "credentials_encrypted", columnDefinition = "jsonb")
  private Map<String, String> credentialsEncrypted;
}
