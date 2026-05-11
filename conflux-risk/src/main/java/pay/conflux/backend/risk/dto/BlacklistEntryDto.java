package pay.conflux.backend.risk.dto;

import java.time.Instant;
import java.util.UUID;
import pay.conflux.backend.risk.entity.BlacklistType;

public record BlacklistEntryDto(
    UUID id, BlacklistType type, String value, String reason, Instant expiresAt) {}
