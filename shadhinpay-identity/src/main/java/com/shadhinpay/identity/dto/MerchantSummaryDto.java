package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.enums.IdentifierType;
import com.shadhinpay.identity.enums.OnboardingStatus;
import com.shadhinpay.identity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record MerchantSummaryDto(
    UUID userId,
    UUID merchantProfileId,
    String identifier,
    IdentifierType identifierType,
    String fullName,
    UserStatus status,
    OnboardingStatus onboardingStatus,
    LocalDateTime createdAt) {}
