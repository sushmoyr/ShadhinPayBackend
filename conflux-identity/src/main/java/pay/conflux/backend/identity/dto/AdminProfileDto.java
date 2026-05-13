package pay.conflux.backend.identity.dto;

import java.util.UUID;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;

public record AdminProfileDto(
    UUID userId,
    UUID adminProfileId,
    String identifier,
    IdentifierType identifierType,
    UserStatus status,
    String department,
    String employeeId,
    AdminTier adminTier) {}
