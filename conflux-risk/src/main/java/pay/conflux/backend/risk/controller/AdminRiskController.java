package pay.conflux.backend.risk.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.risk.constant.RiskRoutes;
import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.dto.RiskCaseDto;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.dto.UpdateRiskRuleRequest;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;
import pay.conflux.backend.risk.usecase.RiskDecision;

/**
 * Admin port for risk management: rules, blacklist, merchant risk profiles, and case review. All
 * endpoints require {@code ADMIN_MANAGER} authority — enforced on the adapter.
 */
public interface AdminRiskController {

  @PostMapping(RiskRoutes.ADMIN_RISK_RULES)
  ResponseEntity<ApiResult<RiskRuleDto>> createRule(@RequestBody CreateRiskRuleRequest request);

  @PutMapping(RiskRoutes.ADMIN_RISK_RULE_BY_ID)
  ResponseEntity<ApiResult<RiskRuleDto>> updateRule(
      @PathVariable UUID id, @RequestBody UpdateRiskRuleRequest request);

  @DeleteMapping(RiskRoutes.ADMIN_RISK_RULE_BY_ID)
  ResponseEntity<ApiResult<Void>> disableRule(@PathVariable UUID id);

  @GetMapping(RiskRoutes.ADMIN_RISK_RULES)
  ResponseEntity<ApiResult<List<RiskRuleDto>>> listRules(Pageable pageable);

  @PostMapping(RiskRoutes.ADMIN_RISK_BLACKLIST)
  ResponseEntity<ApiResult<BlacklistEntryDto>> addBlacklistEntry(
      @RequestBody AddBlacklistEntryRequest request);

  @DeleteMapping(RiskRoutes.ADMIN_RISK_BLACKLIST_BY_ID)
  ResponseEntity<ApiResult<Void>> removeBlacklistEntry(@PathVariable UUID id);

  @GetMapping(RiskRoutes.ADMIN_RISK_BLACKLIST)
  ResponseEntity<ApiResult<List<BlacklistEntryDto>>> listBlacklistEntries(Pageable pageable);

  @PutMapping(RiskRoutes.ADMIN_RISK_PROFILE_BY_ID)
  ResponseEntity<ApiResult<MerchantRiskProfileDto>> upsertProfile(
      @PathVariable UUID merchantId, @RequestBody UpsertMerchantRiskProfileRequest request);

  @GetMapping(RiskRoutes.ADMIN_RISK_PROFILE_BY_ID)
  ResponseEntity<ApiResult<MerchantRiskProfileDto>> getProfile(@PathVariable UUID merchantId);

  @GetMapping(RiskRoutes.ADMIN_RISK_CASES)
  ResponseEntity<ApiResult<List<RiskCaseDto>>> listCases(
      @RequestParam(required = false, defaultValue = "FLAG") RiskDecision.Action status,
      Pageable pageable);

  @PostMapping(RiskRoutes.ADMIN_RISK_CASE_APPROVE)
  ResponseEntity<ApiResult<Void>> approveCase(@PathVariable UUID evaluationId);

  @PostMapping(RiskRoutes.ADMIN_RISK_CASE_REJECT)
  ResponseEntity<ApiResult<Void>> rejectCase(@PathVariable UUID evaluationId);
}
