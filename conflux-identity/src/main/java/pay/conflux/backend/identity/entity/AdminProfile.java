package pay.conflux.backend.identity.entity;

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
import pay.conflux.backend.identity.enums.AdminTier;

@Getter
@Setter
@Entity
@Table(name = "admin_profiles")
public class AdminProfile extends Auditable {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "department", nullable = false)
  private String department;

  @Column(name = "employee_id", nullable = false, unique = true)
  private String employeeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "admin_tier", nullable = false, length = 16)
  private AdminTier adminTier = AdminTier.VIEWER;
}
