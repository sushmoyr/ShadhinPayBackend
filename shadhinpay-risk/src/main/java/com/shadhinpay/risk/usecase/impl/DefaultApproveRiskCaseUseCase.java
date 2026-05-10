package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.internal.ApproveRiskCaseUseCase;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class DefaultApproveRiskCaseUseCase implements ApproveRiskCaseUseCase {

  private final RiskEvaluationRepository riskEvaluationRepository;

  public DefaultApproveRiskCaseUseCase(RiskEvaluationRepository riskEvaluationRepository) {
    this.riskEvaluationRepository = riskEvaluationRepository;
  }

  @Override
  @Transactional
  public void execute(UUID evaluationId) {
    RiskEvaluation evaluation =
        riskEvaluationRepository
            .findById(evaluationId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskEvaluation", evaluationId));

    evaluation.setReviewDecision("APPROVE");
    evaluation.setReviewedAt(Instant.now());
    evaluation.setReviewedByAdminId(
        UUID.randomUUID()); // placeholder; Wave B will use real admin id
    riskEvaluationRepository.save(evaluation);
  }
}
