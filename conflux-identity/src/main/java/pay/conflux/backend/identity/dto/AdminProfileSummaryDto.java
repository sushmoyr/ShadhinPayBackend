package pay.conflux.backend.identity.dto;

import java.util.UUID;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserStatus;

public record AdminProfileSummaryDto(
    UUID userId,
    UUID adminProfileId,
    String identifier,
    UserStatus status,
    String employeeId,
    AdminTier adminTier) {}
