package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.TrustLevel;
import jakarta.validation.constraints.NotNull;

public record UpsertMerchantRiskProfileRequest(
    @NotNull TrustLevel trustLevel, String customLimits) {}
