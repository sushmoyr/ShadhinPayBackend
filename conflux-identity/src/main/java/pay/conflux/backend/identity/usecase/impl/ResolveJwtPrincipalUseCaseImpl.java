package pay.conflux.backend.identity.usecase.impl;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.support.JwtClaims;
import pay.conflux.backend.identity.support.JwtTokenService;
import pay.conflux.backend.identity.usecase.ResolveJwtPrincipalUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ResolveJwtPrincipalUseCaseImpl implements ResolveJwtPrincipalUseCase {

  private final JwtTokenService jwtTokenService;
  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;

  @Override
  @Transactional(readOnly = true)
  public Resolved execute(String jwt) {
    JwtClaims claims = jwtTokenService.parse(jwt);

    Optional<User> userOpt = userRepository.findById(claims.userId());
    if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE) {
      log.warn("JWT rejected: user {} missing or not ACTIVE", claims.userId());
      throw new UnauthorizedException("User is not active");
    }
    User user = userOpt.get();

    String adminTierName = null;
    if (user.getUserType() == UserType.ADMIN) {
      AdminProfile profile =
          adminProfileRepository
              .findByUserId(user.getId())
              .orElseThrow(
                  () -> {
                    log.error(
                        "ADMIN user {} has no AdminProfile (schema FK broken) — refusing JWT",
                        user.getId());
                    return new UnauthorizedException("Admin profile not found");
                  });
      adminTierName = profile.getAdminTier().name();
    }

    AuthenticatedPrincipal.UserType principalType =
        user.getUserType() == UserType.ADMIN
            ? AuthenticatedPrincipal.UserType.ADMIN
            : AuthenticatedPrincipal.UserType.MERCHANT;
    return new Resolved(user.getId(), principalType, adminTierName);
  }
}
