package com.shadhinpay.risk.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.RiskCaseDto;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
import com.shadhinpay.risk.usecase.RiskDecision;
import com.shadhinpay.risk.usecase.internal.AddBlacklistEntryUseCase;
import com.shadhinpay.risk.usecase.internal.ApproveRiskCaseUseCase;
import com.shadhinpay.risk.usecase.internal.CreateRiskRuleUseCase;
import com.shadhinpay.risk.usecase.internal.DisableRiskRuleUseCase;
import com.shadhinpay.risk.usecase.internal.GetMerchantRiskProfileUseCase;
import com.shadhinpay.risk.usecase.internal.ListBlacklistUseCase;
import com.shadhinpay.risk.usecase.internal.ListPendingCasesUseCase;
import com.shadhinpay.risk.usecase.internal.ListRiskRulesUseCase;
import com.shadhinpay.risk.usecase.internal.RejectRiskCaseUseCase;
import com.shadhinpay.risk.usecase.internal.RemoveBlacklistEntryUseCase;
import com.shadhinpay.risk.usecase.internal.UpdateRiskRuleUseCase;
import com.shadhinpay.risk.usecase.internal.UpsertMerchantRiskProfileUseCase;
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
