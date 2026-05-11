package pay.conflux.backend.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import pay.conflux.backend.common.entity.AuditableAndSoftDeletable;

@Entity
@Table(name = "risk_rules")
@Getter
@Setter
public class RiskRule extends AuditableAndSoftDeletable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String expression;

  @Column(name = "score_weight", nullable = false)
  private Integer scoreWeight;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private RuleAction action;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
