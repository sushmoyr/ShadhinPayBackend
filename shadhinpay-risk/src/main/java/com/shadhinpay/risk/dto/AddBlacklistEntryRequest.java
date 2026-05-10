package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.BlacklistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AddBlacklistEntryRequest(
    @NotNull BlacklistType type,
    @NotBlank String value,
    @NotBlank String reason,
    Instant expiresAt) {}
