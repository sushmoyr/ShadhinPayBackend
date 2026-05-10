package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.entity.enums.UserStatus;
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
