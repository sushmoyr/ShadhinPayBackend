package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pay.conflux.backend.common.validator.SafeString;

public record RejectMerchantRequest(
    @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        @SafeString
        String reason) {}
