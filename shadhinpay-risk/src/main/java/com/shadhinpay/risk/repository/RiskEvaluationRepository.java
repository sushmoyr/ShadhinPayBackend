package com.shadhinpay.risk.repository;

import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.usecase.RiskDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluation, UUID> {

  List<RiskEvaluation> findByTransactionId(UUID transactionId);

  Page<RiskEvaluation> findByDecision(RiskDecision.Action decision, Pageable pageable);

  Page<RiskEvaluation> findByDecisionAndReviewDecisionIsNull(
      RiskDecision.Action decision, Pageable pageable);
}
