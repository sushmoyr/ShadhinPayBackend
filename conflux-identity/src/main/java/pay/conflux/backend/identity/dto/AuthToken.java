package pay.conflux.backend.identity.dto;

import java.time.Instant;
import java.util.UUID;
import pay.conflux.backend.identity.enums.UserType;

/**
 * A stub record representing an authentication token payload. This will be replaced by a full JWT
 * implementation in later waves.
 */
public record AuthToken(UUID userId, UserType userType, Instant issuedAt, Instant expiresAt) {}
