package pay.conflux.backend.identity.support;

import java.util.UUID;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserType;

/**
 * Decoded form of a Wave D JWT. {@link #tier} is non-null iff {@link #userType} is {@code ADMIN};
 * the schema FK guarantees admin users always have an {@code AdminProfile} with a tier.
 */
public record JwtClaims(UUID userId, UserType userType, AdminTier tier) {}
