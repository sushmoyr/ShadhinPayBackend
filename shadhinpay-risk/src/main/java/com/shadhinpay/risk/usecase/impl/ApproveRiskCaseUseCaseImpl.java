package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.security.SecurityUtils;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.internal.ApproveRiskCaseUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class ApproveRiskCaseUseCaseImpl implements ApproveRiskCaseUseCase {

  private final RiskEvaluationRepository riskEvaluationRepository;

  @Override
  @Transactional
  public void execute(UUID evaluationId) {
    UUID adminId =
        SecurityUtils.currentAdminId()
            .orElseThrow(
                () ->
                    new InvalidOperationStateException(
                        "Risk case review requires an authenticated admin"));

    RiskEvaluation evaluation =
        riskEvaluationRepository
            .findById(evaluationId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskEvaluation", evaluationId));

    evaluation.setReviewDecision("APPROVE");
    evaluation.setReviewedAt(Instant.now());
    evaluation.setReviewedByAdminId(adminId);
    riskEvaluationRepository.save(evaluation);
  }
}
