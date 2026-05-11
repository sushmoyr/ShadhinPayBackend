package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.risk.dto.RiskCaseDto;
import pay.conflux.backend.risk.entity.RiskEvaluation;
import pay.conflux.backend.risk.repository.RiskEvaluationRepository;
import pay.conflux.backend.risk.usecase.RiskDecision;

@DisplayName("Case Management Use Cases")
class CaseManagementUseCaseTest {

  private static final UUID ADMIN_ID = UUID.randomUUID();

  private RiskEvaluationRepository riskEvaluationRepository;
  private ListPendingCasesUseCaseImpl listPendingCasesUseCase;
  private ApproveRiskCaseUseCaseImpl approveRiskCaseUseCase;
  private RejectRiskCaseUseCaseImpl rejectRiskCaseUseCase;

  @BeforeEach
  void setUp() {
    riskEvaluationRepository = mock(RiskEvaluationRepository.class);
    listPendingCasesUseCase = new ListPendingCasesUseCaseImpl(riskEvaluationRepository);
    approveRiskCaseUseCase = new ApproveRiskCaseUseCaseImpl(riskEvaluationRepository);
    rejectRiskCaseUseCase = new RejectRiskCaseUseCaseImpl(riskEvaluationRepository);

    AuthenticatedPrincipal admin =
        new AuthenticatedPrincipal(
            ADMIN_ID,
            AuthenticatedPrincipal.UserType.ADMIN,
            null,
            null,
            AuthenticatedPrincipal.Environment.TEST);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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

    Page<RiskCaseDto> result =
        listPendingCasesUseCase.execute(RiskDecision.Action.FLAG, PageRequest.of(0, 20));

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    RiskCaseDto dto = result.getContent().get(0);
    assertEquals(eval.getId(), dto.id());
    assertEquals(RiskDecision.Action.FLAG, dto.decision());
    assertNull(dto.reviewDecision());
  }

  @Test
  @DisplayName("listPendingCases defaults to FLAG when status is null")
  void listPendingCasesDefault() {
    when(riskEvaluationRepository.findByDecisionAndReviewDecisionIsNull(
            eq(RiskDecision.Action.FLAG), any()))
        .thenReturn(new PageImpl<>(List.of()));

    Page<RiskCaseDto> result = listPendingCasesUseCase.execute(null, PageRequest.of(0, 20));

    assertNotNull(result);
    verify(riskEvaluationRepository)
        .findByDecisionAndReviewDecisionIsNull(eq(RiskDecision.Action.FLAG), any());
  }

  @Test
  @DisplayName("approveCase records APPROVE decision with authenticated admin id")
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
    assertEquals(ADMIN_ID, eval.getReviewedByAdminId());
    verify(riskEvaluationRepository).save(eval);
  }

  @Test
  @DisplayName("rejectCase records REJECT decision with authenticated admin id")
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
    assertEquals(ADMIN_ID, eval.getReviewedByAdminId());
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

  @Test
  @DisplayName("approveCase requires an authenticated admin")
  void approveCaseRequiresAdmin() {
    SecurityContextHolder.clearContext();
    UUID evaluationId = UUID.randomUUID();

    assertThrows(
        InvalidOperationStateException.class, () -> approveRiskCaseUseCase.execute(evaluationId));
  }
}
