package pay.conflux.backend.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.risk.usecase.RiskDecision;

@Entity
@Table(name = "risk_evaluations")
@Getter
@Setter
public class RiskEvaluation extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "merchant_id", nullable = false)
  private UUID merchantId;

  @Column(name = "total_score", nullable = false)
  private Integer totalScore;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private RiskDecision.Action decision;

  @Convert(converter = UuidListConverter.class)
  @Column(name = "triggered_rule_ids", columnDefinition = "jsonb")
  private List<UUID> triggeredRuleIds = new ArrayList<>();

  @Column(columnDefinition = "TEXT")
  private String reason;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;

  @Column(name = "reviewed_by_admin_id")
  private UUID reviewedByAdminId;

  @Column(name = "review_decision", length = 16)
  private String reviewDecision;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;
}
