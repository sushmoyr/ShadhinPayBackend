package com.shadhinpay.risk.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.risk.constant.RiskRoutes;
import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.RiskCaseDto;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ADMIN_MANAGER')")
public class AdminRiskController {

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

  public AdminRiskController(
      CreateRiskRuleUseCase createRiskRuleUseCase,
      UpdateRiskRuleUseCase updateRiskRuleUseCase,
      DisableRiskRuleUseCase disableRiskRuleUseCase,
      ListRiskRulesUseCase listRiskRulesUseCase,
      AddBlacklistEntryUseCase addBlacklistEntryUseCase,
      RemoveBlacklistEntryUseCase removeBlacklistEntryUseCase,
      ListBlacklistUseCase listBlacklistUseCase,
      UpsertMerchantRiskProfileUseCase upsertMerchantRiskProfileUseCase,
      GetMerchantRiskProfileUseCase getMerchantRiskProfileUseCase,
      ListPendingCasesUseCase listPendingCasesUseCase,
      ApproveRiskCaseUseCase approveRiskCaseUseCase,
      RejectRiskCaseUseCase rejectRiskCaseUseCase) {
    this.createRiskRuleUseCase = createRiskRuleUseCase;
    this.updateRiskRuleUseCase = updateRiskRuleUseCase;
    this.disableRiskRuleUseCase = disableRiskRuleUseCase;
    this.listRiskRulesUseCase = listRiskRulesUseCase;
    this.addBlacklistEntryUseCase = addBlacklistEntryUseCase;
    this.removeBlacklistEntryUseCase = removeBlacklistEntryUseCase;
    this.listBlacklistUseCase = listBlacklistUseCase;
    this.upsertMerchantRiskProfileUseCase = upsertMerchantRiskProfileUseCase;
    this.getMerchantRiskProfileUseCase = getMerchantRiskProfileUseCase;
    this.listPendingCasesUseCase = listPendingCasesUseCase;
    this.approveRiskCaseUseCase = approveRiskCaseUseCase;
    this.rejectRiskCaseUseCase = rejectRiskCaseUseCase;
  }

  @PostMapping(RiskRoutes.ADMIN_RISK_RULES)
  public ResponseEntity<ApiResult<RiskRuleDto>> createRule(
      @Valid @RequestBody CreateRiskRuleRequest request) {
    return ApiResult.created(createRiskRuleUseCase.execute(request));
  }

  @PutMapping(RiskRoutes.ADMIN_RISK_RULE_BY_ID)
  public ResponseEntity<ApiResult<RiskRuleDto>> updateRule(
      @PathVariable UUID id, @Valid @RequestBody UpdateRiskRuleRequest request) {
    return ApiResult.ok(updateRiskRuleUseCase.execute(id, request));
  }

  @DeleteMapping(RiskRoutes.ADMIN_RISK_RULE_BY_ID)
  public ResponseEntity<ApiResult<Void>> disableRule(@PathVariable UUID id) {
    disableRiskRuleUseCase.execute(id);
    return ApiResult.ok();
  }

  @GetMapping(RiskRoutes.ADMIN_RISK_RULES)
  public ResponseEntity<ApiResult<List<RiskRuleDto>>> listRules(Pageable pageable) {
    return ApiResult.ok(listRiskRulesUseCase.execute(pageable));
  }

  @PostMapping(RiskRoutes.ADMIN_RISK_BLACKLIST)
  public ResponseEntity<ApiResult<BlacklistEntryDto>> addBlacklistEntry(
      @Valid @RequestBody AddBlacklistEntryRequest request) {
    return ApiResult.created(addBlacklistEntryUseCase.execute(request));
  }

  @DeleteMapping(RiskRoutes.ADMIN_RISK_BLACKLIST_BY_ID)
  public ResponseEntity<ApiResult<Void>> removeBlacklistEntry(@PathVariable UUID id) {
    removeBlacklistEntryUseCase.execute(id);
    return ApiResult.ok();
  }

  @GetMapping(RiskRoutes.ADMIN_RISK_BLACKLIST)
  public ResponseEntity<ApiResult<List<BlacklistEntryDto>>> listBlacklistEntries(
      Pageable pageable) {
    return ApiResult.ok(listBlacklistUseCase.execute(pageable));
  }

  @PutMapping(RiskRoutes.ADMIN_RISK_PROFILE_BY_ID)
  public ResponseEntity<ApiResult<MerchantRiskProfileDto>> upsertProfile(
      @PathVariable UUID merchantId, @Valid @RequestBody UpsertMerchantRiskProfileRequest request) {
    return ApiResult.ok(upsertMerchantRiskProfileUseCase.execute(merchantId, request));
  }

  @GetMapping(RiskRoutes.ADMIN_RISK_PROFILE_BY_ID)
  public ResponseEntity<ApiResult<MerchantRiskProfileDto>> getProfile(
      @PathVariable UUID merchantId) {
    return ApiResult.ok(getMerchantRiskProfileUseCase.execute(merchantId));
  }

  @GetMapping(RiskRoutes.ADMIN_RISK_CASES)
  public ResponseEntity<ApiResult<List<RiskCaseDto>>> listCases(Pageable pageable) {
    return ApiResult.ok(listPendingCasesUseCase.execute(pageable));
  }

  @PostMapping(RiskRoutes.ADMIN_RISK_CASE_APPROVE)
  public ResponseEntity<ApiResult<Void>> approveCase(@PathVariable UUID evaluationId) {
    approveRiskCaseUseCase.execute(evaluationId);
    return ApiResult.ok();
  }

  @PostMapping(RiskRoutes.ADMIN_RISK_CASE_REJECT)
  public ResponseEntity<ApiResult<Void>> rejectCase(@PathVariable UUID evaluationId) {
    rejectRiskCaseUseCase.execute(evaluationId);
    return ApiResult.ok();
  }
}
