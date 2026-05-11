package pay.conflux.backend.risk.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.dto.RiskCaseDto;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.dto.UpdateRiskRuleRequest;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;
import pay.conflux.backend.risk.usecase.RiskDecision;
import pay.conflux.backend.risk.usecase.internal.AddBlacklistEntryUseCase;
import pay.conflux.backend.risk.usecase.internal.ApproveRiskCaseUseCase;
import pay.conflux.backend.risk.usecase.internal.CreateRiskRuleUseCase;
import pay.conflux.backend.risk.usecase.internal.DisableRiskRuleUseCase;
import pay.conflux.backend.risk.usecase.internal.GetMerchantRiskProfileUseCase;
import pay.conflux.backend.risk.usecase.internal.ListBlacklistUseCase;
import pay.conflux.backend.risk.usecase.internal.ListPendingCasesUseCase;
import pay.conflux.backend.risk.usecase.internal.ListRiskRulesUseCase;
import pay.conflux.backend.risk.usecase.internal.RejectRiskCaseUseCase;
import pay.conflux.backend.risk.usecase.internal.RemoveBlacklistEntryUseCase;
import pay.conflux.backend.risk.usecase.internal.UpdateRiskRuleUseCase;
import pay.conflux.backend.risk.usecase.internal.UpsertMerchantRiskProfileUseCase;

@RestController
@RequiredArgsConstructor
public class AdminRiskControllerImpl implements AdminRiskController {

  private final CreateRiskRuleUseCase createRiskRuleUseCase;
  private final UpdateRiskRuleUseCase updateRiskRuleUseCase;
  private final DisableRiskRuleUseCase disableRiskRuleUseCase;
  private final ListRiskRulesUseCase listRiskRulesUseCase;
  private final AddBlacklistEntryUseCase addBlacklistEntryUseCase;
  private final RemoveBlacklistEntryUseCase removeBlacklistEntryUseCase;
  private final ListBlacklistUseCase listBlacklistUseCase;
  private final UpsertMerchantRiskProfileUseCase upsertMerchantRiskProfileUseCase;
  private final GetMerchantRiskProfileUseCase getMerchantRiskProfileUseCase;
  private final ListPendingCasesUseCase listPendingCasesUseCase;
  private final ApproveRiskCaseUseCase approveRiskCaseUseCase;
  private final RejectRiskCaseUseCase rejectRiskCaseUseCase;

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<RiskRuleDto>> createRule(
      @Valid @RequestBody CreateRiskRuleRequest request) {
    return ApiResult.created(createRiskRuleUseCase.execute(request));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<RiskRuleDto>> updateRule(
      @PathVariable UUID id, @Valid @RequestBody UpdateRiskRuleRequest request) {
    return ApiResult.ok(updateRiskRuleUseCase.execute(id, request));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> disableRule(@PathVariable UUID id) {
    disableRiskRuleUseCase.execute(id);
    return ApiResult.ok();
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<List<RiskRuleDto>>> listRules(Pageable pageable) {
    return ApiResult.ok(listRiskRulesUseCase.execute(pageable));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<BlacklistEntryDto>> addBlacklistEntry(
      @Valid @RequestBody AddBlacklistEntryRequest request) {
    return ApiResult.created(addBlacklistEntryUseCase.execute(request));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> removeBlacklistEntry(@PathVariable UUID id) {
    removeBlacklistEntryUseCase.execute(id);
    return ApiResult.ok();
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<List<BlacklistEntryDto>>> listBlacklistEntries(
      Pageable pageable) {
    return ApiResult.ok(listBlacklistUseCase.execute(pageable));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<MerchantRiskProfileDto>> upsertProfile(
      @PathVariable UUID merchantId, @Valid @RequestBody UpsertMerchantRiskProfileRequest request) {
    return ApiResult.ok(upsertMerchantRiskProfileUseCase.execute(merchantId, request));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<MerchantRiskProfileDto>> getProfile(
      @PathVariable UUID merchantId) {
    return ApiResult.ok(getMerchantRiskProfileUseCase.execute(merchantId));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<List<RiskCaseDto>>> listCases(
      @RequestParam(required = false, defaultValue = "FLAG") RiskDecision.Action status,
      Pageable pageable) {
    return ApiResult.ok(listPendingCasesUseCase.execute(status, pageable));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> approveCase(@PathVariable UUID evaluationId) {
    approveRiskCaseUseCase.execute(evaluationId);
    return ApiResult.ok();
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> rejectCase(@PathVariable UUID evaluationId) {
    rejectRiskCaseUseCase.execute(evaluationId);
    return ApiResult.ok();
  }
}
