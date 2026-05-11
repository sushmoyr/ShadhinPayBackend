package pay.conflux.backend.identity.dto;

import java.util.UUID;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.enums.UserStatus;

public record MerchantOnboardingDto(
    UUID userId,
    String identifier,
    IdentifierType identifierType,
    UserStatus status,
    String fullName,
    OnboardingStatus onboardingStatus) {}
