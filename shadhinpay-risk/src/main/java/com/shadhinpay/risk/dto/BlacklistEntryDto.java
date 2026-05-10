package com.shadhinpay.risk.dto;

import com.shadhinpay.risk.entity.BlacklistType;
import java.time.Instant;
import java.util.UUID;

public record BlacklistEntryDto(
    UUID id, BlacklistType type, String value, String reason, Instant expiresAt) {}
