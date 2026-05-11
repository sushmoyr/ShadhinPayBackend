package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.risk.dto.RiskCaseDto;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.RiskDecision;
import com.shadhinpay.risk.usecase.internal.ListPendingCasesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
