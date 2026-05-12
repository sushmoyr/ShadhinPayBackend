package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pay.conflux.backend.common.validator.SafeString;
import pay.conflux.backend.identity.enums.AdminTier;

public record CreateAdminRequest(
    @NotBlank @Size(max = 255) @SafeString String identifier,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 100) @SafeString String department,
    @NotBlank @Size(max = 50) @SafeString String employeeId,
    @NotNull AdminTier adminTier) {}
