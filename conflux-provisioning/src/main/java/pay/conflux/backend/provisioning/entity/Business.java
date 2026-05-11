package pay.conflux.backend.provisioning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pay.conflux.backend.common.entity.AuditableAndSoftDeletable;
import pay.conflux.backend.provisioning.constant.BusinessStatus;

@Entity
@Table(
    name = "businesses",
    indexes = {
      @Index(name = "idx_businesses_merchant_deleted", columnList = "merchant_id, deleted")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Business extends AuditableAndSoftDeletable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "name", nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(name = "display_name", nullable = false, columnDefinition = "TEXT")
  private String displayName;

  @Column(name = "logo_url", columnDefinition = "TEXT")
  private String logoUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BusinessStatus status = BusinessStatus.ACTIVE;

  @Column(name = "webhook_url", columnDefinition = "TEXT")
  private String webhookUrl;

  @Column(name = "webhook_secret_encrypted", columnDefinition = "TEXT")
  private String webhookSecretEncrypted;
}
