package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.entity.enums.UserType;
import java.time.Instant;
import java.util.UUID;

/**
 * A stub record representing an authentication token payload. This will be replaced by a full JWT
 * implementation in later waves.
 */
public record AuthToken(UUID userId, UserType userType, Instant issuedAt, Instant expiresAt) {}
