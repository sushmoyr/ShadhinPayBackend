package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.TrustLevel;
import java.util.UUID;

public record MerchantRiskProfileDto(UUID merchantId, TrustLevel trustLevel, String customLimits) {}
