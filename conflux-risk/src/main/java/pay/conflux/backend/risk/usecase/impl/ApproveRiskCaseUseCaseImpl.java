package pay.conflux.backend.risk.usecase.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.risk.entity.RiskEvaluation;
import pay.conflux.backend.risk.repository.RiskEvaluationRepository;
import pay.conflux.backend.risk.usecase.internal.ApproveRiskCaseUseCase;

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
