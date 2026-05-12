package pay.conflux.backend.risk.dto;

import java.util.UUID;
import pay.conflux.backend.risk.enums.RuleAction;

public record RiskRuleDto(
    UUID id,
    String name,
    String expression,
    Integer scoreWeight,
    RuleAction action,
    boolean active) {}
