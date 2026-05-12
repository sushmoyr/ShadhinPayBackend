package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotNull;
import pay.conflux.backend.identity.enums.AdminTier;

public record UpdateAdminTierRequest(@NotNull AdminTier newTier) {}
