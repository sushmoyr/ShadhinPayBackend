package pay.conflux.backend.identity.usecase.impl;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.util.IdentifierDetector;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.support.JwtTokenService;
import pay.conflux.backend.identity.usecase.AuthenticateUserUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

  /**
   * Pre-computed BCrypt hash compared against when the lookup misses, so the failure path takes the
   * same time as a real password check. Closes the username-enumeration timing side channel.
   */
  private static final String DUMMY_HASH = new BCryptPasswordEncoder(10).encode("__dummy__");

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;

  @Override
  @Transactional
  public LoginResponse execute(LoginRequest request) {
    pay.conflux.backend.common.util.IdentifierType commonType =
        IdentifierDetector.detect(request.identifier());
    IdentifierType identifierType = IdentifierType.valueOf(commonType.name());

    Optional<User> userOpt =
        userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            request.identifier(), identifierType);

    String hashToCompare = userOpt.map(User::getPasswordHash).orElse(DUMMY_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);

    if (userOpt.isEmpty() || !passwordMatches) {
      log.warn("Login failed: invalid credentials");
      throw new UnauthorizedException("Invalid credentials");
    }

    User user = userOpt.get();
    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Login failed: user {} status {}", user.getId(), user.getStatus());
      throw new UnauthorizedException("Invalid credentials");
    }

    AdminProfile adminProfile = null;
    if (user.getUserType() == UserType.ADMIN) {
      adminProfile =
          adminProfileRepository
              .findByUserId(user.getId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "ADMIN user "
                              + user.getId()
                              + " has no AdminProfile (schema FK broken)"));
    }

    user.setLastLoginAt(OffsetDateTime.now());
    userRepository.save(user);

    String token = jwtTokenService.issue(user, adminProfile);
    log.info("Login success for user {}", user.getId());

    return new LoginResponse(token, user.getId(), user.getUserType());
  }
}
