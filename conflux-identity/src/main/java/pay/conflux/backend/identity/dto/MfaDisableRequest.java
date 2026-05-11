package pay.conflux.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import pay.conflux.backend.common.validator.SafeString;

public record MfaDisableRequest(@NotBlank @SafeString String password) {}
