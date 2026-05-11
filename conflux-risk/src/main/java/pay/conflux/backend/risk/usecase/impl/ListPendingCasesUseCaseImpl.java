package pay.conflux.backend.risk.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.risk.dto.RiskCaseDto;
import pay.conflux.backend.risk.entity.RiskEvaluation;
import pay.conflux.backend.risk.repository.RiskEvaluationRepository;
import pay.conflux.backend.risk.usecase.RiskDecision;
import pay.conflux.backend.risk.usecase.internal.ListPendingCasesUseCase;

@UseCase
@RequiredArgsConstructor
public class ListPendingCasesUseCaseImpl implements ListPendingCasesUseCase {

  private final RiskEvaluationRepository riskEvaluationRepository;

  @Override
  public Page<RiskCaseDto> execute(RiskDecision.Action status, Pageable pageable) {
    RiskDecision.Action effective = status == null ? RiskDecision.Action.FLAG : status;
    return riskEvaluationRepository
        .findByDecisionAndReviewDecisionIsNull(effective, pageable)
        .map(this::toDto);
  }

  private RiskCaseDto toDto(RiskEvaluation entity) {
    return new RiskCaseDto(
        entity.getId(),
        entity.getTransactionId(),
        entity.getMerchantId(),
        entity.getTotalScore(),
        entity.getDecision(),
        entity.getTriggeredRuleIds(),
        entity.getReason(),
        entity.getEvaluatedAt(),
        entity.getReviewedByAdminId(),
        entity.getReviewDecision(),
        entity.getReviewedAt());
  }
}
