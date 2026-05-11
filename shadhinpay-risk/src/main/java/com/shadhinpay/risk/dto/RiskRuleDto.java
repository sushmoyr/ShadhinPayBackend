package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.RuleAction;
import java.util.UUID;

public record RiskRuleDto(
    UUID id,
    String name,
    String expression,
    Integer scoreWeight,
    RuleAction action,
    boolean active) {}
