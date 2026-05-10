package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.RuleAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRiskRuleRequest(
    @NotBlank String name,
    @NotBlank String expression,
    @NotNull Integer scoreWeight,
    @NotNull RuleAction action) {}
