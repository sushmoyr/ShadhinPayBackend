package pay.conflux.backend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import pay.conflux.backend.risk.entity.BlacklistType;

public record AddBlacklistEntryRequest(
    @NotNull BlacklistType type,
    @NotBlank String value,
    @NotBlank String reason,
    Instant expiresAt) {}
