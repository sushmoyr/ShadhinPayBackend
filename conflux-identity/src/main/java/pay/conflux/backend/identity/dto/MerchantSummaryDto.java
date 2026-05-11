package pay.conflux.backend.identity.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.enums.UserStatus;

public record MerchantSummaryDto(
    UUID userId,
    UUID merchantProfileId,
    String identifier,
    IdentifierType identifierType,
    String fullName,
    UserStatus status,
    OnboardingStatus onboardingStatus,
    LocalDateTime createdAt) {}
