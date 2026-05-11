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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.provisioning.constant.Environment;

@Entity
@Table(
    name = "api_keys",
    indexes = {
      @Index(
          name = "idx_api_keys_business_env_revoked",
          columnList = "business_id, environment, revoked")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "key_hash", nullable = false, unique = true, columnDefinition = "TEXT")
  private String keyHash;

  @Column(name = "key_prefix", nullable = false, columnDefinition = "TEXT")
  private String keyPrefix;

  @Column(name = "last_four", nullable = false, columnDefinition = "TEXT")
  private String lastFour;

  @Enumerated(EnumType.STRING)
  @Column(name = "environment", nullable = false)
  private Environment environment;

  @Column(name = "revoked", nullable = false)
  private boolean revoked;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;
}
