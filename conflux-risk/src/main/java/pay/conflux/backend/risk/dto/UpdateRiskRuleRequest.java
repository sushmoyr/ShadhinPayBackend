package pay.conflux.backend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pay.conflux.backend.risk.entity.RuleAction;

public record UpdateRiskRuleRequest(
    @NotBlank String expression, @NotNull Integer scoreWeight, @NotNull RuleAction action) {}
