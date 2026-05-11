package pay.conflux.backend.risk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pay.conflux.backend.risk.usecase.RiskDecision;

public record RiskCaseDto(
    UUID id,
    UUID transactionId,
    UUID merchantId,
    Integer totalScore,
    RiskDecision.Action decision,
    List<UUID> triggeredRuleIds,
    String reason,
    Instant evaluatedAt,
    UUID reviewedByAdminId,
    String reviewDecision,
    Instant reviewedAt) {}
