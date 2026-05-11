package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import pay.conflux.backend.common.validator.SafeString;

public record KycSubmissionRequest(
    @NotBlank(message = "NID front URL is required")
        @URL(message = "Must be a valid URL")
        @SafeString
        String nidFrontUrl,
    @NotBlank(message = "NID back URL is required")
        @URL(message = "Must be a valid URL")
        @SafeString
        String nidBackUrl,
    @NotBlank(message = "Trade license URL is required")
        @URL(message = "Must be a valid URL")
        @SafeString
        String tradeLicenseUrl,
    @URL(message = "Must be a valid URL") @SafeString String tinUrl) {}
