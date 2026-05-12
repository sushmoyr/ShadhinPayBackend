package pay.conflux.backend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pay.conflux.backend.risk.enums.RuleAction;

public record CreateRiskRuleRequest(
    @NotBlank String name,
    @NotBlank String expression,
    @NotNull Integer scoreWeight,
    @NotNull RuleAction action) {}
