package pay.conflux.backend.identity.usecase;

import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;

public interface AuthenticateUserUseCase {
  LoginResponse execute(LoginRequest request);
}
