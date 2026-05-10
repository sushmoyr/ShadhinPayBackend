package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.RiskCaseDto;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.RiskDecision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("Case Management Use Cases")
class CaseManagementUseCaseTest {

  private RiskEvaluationRepository riskEvaluationRepository;
  private DefaultListPendingCasesUseCase listPendingCasesUseCase;
  private DefaultApproveRiskCaseUseCase approveRiskCaseUseCase;
  private DefaultRejectRiskCaseUseCase rejectRiskCaseUseCase;

  @BeforeEach
  void setUp() {
    riskEvaluationRepository = mock(RiskEvaluationRepository.class);
    listPendingCasesUseCase = new DefaultListPendingCasesUseCase(riskEvaluationRepository);
    approveRiskCaseUseCase = new DefaultApproveRiskCaseUseCase(riskEvaluationRepository);
    rejectRiskCaseUseCase = new DefaultRejectRiskCaseUseCase(riskEvaluationRepository);
  }

  @Test
  @DisplayName("listPendingCases returns paginated FLAG evaluations with no review")
  void listPendingCases() {
    RiskEvaluation eval = new RiskEvaluation();
    eval.setId(UUID.randomUUID());
    eval.setTransactionId(UUID.randomUUID());
    eval.setMerchantId(UUID.randomUUID());
    eval.setTotalScore(60);
    eval.setDecision(RiskDecision.Action.FLAG);
    eval.setTriggeredRuleIds(List.of());
    eval.setReason("Score 60 >= threshold 50");
    eval.setEvaluatedAt(Instant.now());

    Page<RiskEvaluation> page = new PageImpl<>(List.of(eval), PageRequest.of(0, 20), 1);
    when(riskEvaluationRepository.findByDecisionAndReviewDecisionIsNull(
            eq(RiskDecision.Action.FLAG), any()))
        .thenReturn(page);

    Page<RiskCaseDto> result = listPendingCasesUseCase.execute(PageRequest.of(0, 20));

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    RiskCaseDto dto = result.getContent().get(0);
    assertEquals(eval.getId(), dto.id());
    assertEquals(RiskDecision.Action.FLAG, dto.decision());
    assertNull(dto.reviewDecision());
  }

  @Test
  @DisplayName("approveCase records APPROVE decision")
  void approveCase() {
    UUID evaluationId = UUID.randomUUID();
    RiskEvaluation eval = new RiskEvaluation();
    eval.setId(evaluationId);
    eval.setDecision(RiskDecision.Action.FLAG);

    when(riskEvaluationRepository.findById(evaluationId)).thenReturn(Optional.of(eval));
    when(riskEvaluationRepository.save(any(RiskEvaluation.class)))
        .thenAnswer(i -> i.getArgument(0));

    approveRiskCaseUseCase.execute(evaluationId);

    assertEquals("APPROVE", eval.getReviewDecision());
    assertNotNull(eval.getReviewedAt());
    assertNotNull(eval.getReviewedByAdminId());
    verify(riskEvaluationRepository).save(eval);
  }

  @Test
  @DisplayName("rejectCase records REJECT decision")
  void rejectCase() {
    UUID evaluationId = UUID.randomUUID();
    RiskEvaluation eval = new RiskEvaluation();
    eval.setId(evaluationId);
    eval.setDecision(RiskDecision.Action.FLAG);

    when(riskEvaluationRepository.findById(evaluationId)).thenReturn(Optional.of(eval));
    when(riskEvaluationRepository.save(any(RiskEvaluation.class)))
        .thenAnswer(i -> i.getArgument(0));

    rejectRiskCaseUseCase.execute(evaluationId);

    assertEquals("REJECT", eval.getReviewDecision());
    assertNotNull(eval.getReviewedAt());
    assertNotNull(eval.getReviewedByAdminId());
    verify(riskEvaluationRepository).save(eval);
  }

  @Test
  @DisplayName("approveCase throws ResourceNotFoundException for missing evaluation")
  void approveCaseNotFound() {
    UUID evaluationId = UUID.randomUUID();
    when(riskEvaluationRepository.findById(evaluationId)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> approveRiskCaseUseCase.execute(evaluationId));
  }

  @Test
  @DisplayName("rejectCase throws ResourceNotFoundException for missing evaluation")
  void rejectCaseNotFound() {
    UUID evaluationId = UUID.randomUUID();
    when(riskEvaluationRepository.findById(evaluationId)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> rejectRiskCaseUseCase.execute(evaluationId));
  }
}
