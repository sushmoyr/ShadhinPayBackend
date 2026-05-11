package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pay.conflux.backend.common.validator.SafeString;

public record RegisterMerchantRequest(
    @NotBlank @Size(min = 3, max = 255) @SafeString String identifier,
    @NotBlank @Size(min = 8, max = 72) @SafeString String password,
    @NotBlank @Size(min = 2, max = 255) @SafeString String fullName) {}
