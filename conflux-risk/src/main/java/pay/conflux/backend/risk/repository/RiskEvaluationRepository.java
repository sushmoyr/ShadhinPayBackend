package pay.conflux.backend.risk.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.risk.entity.RiskEvaluation;
import pay.conflux.backend.risk.usecase.RiskDecision;

@Repository
public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluation, UUID> {

  List<RiskEvaluation> findByTransactionId(UUID transactionId);

  Page<RiskEvaluation> findByDecision(RiskDecision.Action decision, Pageable pageable);

  Page<RiskEvaluation> findByDecisionAndReviewDecisionIsNull(
      RiskDecision.Action decision, Pageable pageable);
}
