package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.usecase.RiskDecision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
