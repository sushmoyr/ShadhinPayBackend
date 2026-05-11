package pay.conflux.backend.identity.dto;

import java.util.UUID;
import pay.conflux.backend.identity.enums.UserType;

public record LoginResponse(String authToken, UUID userId, UserType userType) {}
