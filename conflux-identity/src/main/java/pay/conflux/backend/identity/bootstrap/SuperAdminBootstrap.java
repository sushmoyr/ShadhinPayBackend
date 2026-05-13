package pay.conflux.backend.identity.bootstrap;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.util.IdentifierDetector;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

/**
 * Seeds (or reconciles) the SUPER admin account on every boot per {@code
 * DOCS/features/identity/TECH_SPEC.md §4.4}. Writes via repositories — does NOT call {@link
 * pay.conflux.backend.identity.usecase.CreateAdminUseCase} because that use case requires an
 * authenticated SUPER caller (chicken-and-egg on a fresh database).
 *
 * <p>Six-step contract:
 *
 * <ol>
 *   <li>If both {@code identifier} and {@code password} are blank AND no active SUPER admin exists,
 *       fail fast with {@link IllegalStateException} — refuse to boot a system with no super-admin
 *       path.
 *   <li>If both env vars are blank but a SUPER admin already exists (e.g., seeded via
 *       {@code @Sql}), log {@code noop} and return.
 *   <li>If the configured identifier does not yet exist, create the User + AdminProfile (tier =
 *       {@link AdminTier#SUPER}, status = {@link UserStatus#ACTIVE}); log {@code created}.
 *   <li>If the identifier exists but its stored BCrypt does not match the configured password,
 *       rotate the hash; log {@code rotated}.
 *   <li>If the existing user is not currently {@code SUPER} or not {@code ACTIVE}, reconcile to
 *       {@code SUPER}/{@code ACTIVE}; log {@code reconciled}.
 *   <li>Otherwise the configured state already matches; log {@code noop}.
 * </ol>
 *
 * <p>Never logs the configured password (plaintext or hash) or the full identifier.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

  static final String DEFAULT_DEPARTMENT = "Bootstrap";
  static final String DEFAULT_EMPLOYEE_ID = "BOOTSTRAP-SUPER";

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${conflux.identity.super-admin.identifier:}")
  private String configuredIdentifier;

  @Value("${conflux.identity.super-admin.password:}")
  private String configuredPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    boolean identifierBlank = configuredIdentifier == null || configuredIdentifier.isBlank();
    boolean passwordBlank = configuredPassword == null || configuredPassword.isBlank();

    if (identifierBlank && passwordBlank) {
      long superCount = adminProfileRepository.countActiveSuperAdmins();
      if (superCount == 0) {
        throw new IllegalStateException(
            "No active SUPER admin exists and conflux.identity.super-admin.{identifier,password}"
                + " are not configured. Set SUPER_ADMIN_IDENTIFIER + SUPER_ADMIN_PASSWORD on first"
                + " boot.");
      }
      log.info(
          "SuperAdminBootstrap: noop (env vars blank, {} active SUPER admin(s) already exist)",
          superCount);
      return;
    }
    if (identifierBlank || passwordBlank) {
      throw new IllegalStateException(
          "conflux.identity.super-admin.identifier and .password must both be set or both blank");
    }

    pay.conflux.backend.common.util.IdentifierType commonType =
        IdentifierDetector.detect(configuredIdentifier);
    IdentifierType identifierType = IdentifierType.valueOf(commonType.name());

    Optional<User> existing =
        userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            configuredIdentifier, identifierType);

    if (existing.isEmpty()) {
      User created = createSuper(configuredIdentifier, configuredPassword, identifierType);
      log.info("SuperAdminBootstrap: created SUPER admin userId={}", created.getId());
      return;
    }

    User user = existing.get();
    if (user.getUserType() != UserType.ADMIN) {
      throw new IllegalStateException(
          "Configured SUPER admin identifier already belongs to a non-admin user " + user.getId());
    }
    AdminProfile profile =
        adminProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "ADMIN user " + user.getId() + " has no AdminProfile"));

    boolean reconciled = false;
    if (profile.getAdminTier() != AdminTier.SUPER) {
      profile.setAdminTier(AdminTier.SUPER);
      adminProfileRepository.save(profile);
      reconciled = true;
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      user.setStatus(UserStatus.ACTIVE);
      reconciled = true;
    }
    boolean passwordMatches = passwordEncoder.matches(configuredPassword, user.getPasswordHash());
    if (!passwordMatches) {
      user.setPasswordHash(passwordEncoder.encode(configuredPassword));
      userRepository.save(user);
      log.info("SuperAdminBootstrap: rotated SUPER admin password userId={}", user.getId());
      return;
    }
    if (reconciled) {
      userRepository.save(user);
      log.info("SuperAdminBootstrap: reconciled SUPER admin tier/status userId={}", user.getId());
      return;
    }
    log.info("SuperAdminBootstrap: noop (configured SUPER admin matches) userId={}", user.getId());
  }

  private User createSuper(String identifier, String password, IdentifierType identifierType) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier(identifier);
    user.setIdentifierType(identifierType);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setUserType(UserType.ADMIN);
    user.setStatus(UserStatus.ACTIVE);
    user.setMfaEnabled(false);
    userRepository.save(user);

    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(user.getId());
    profile.setDepartment(DEFAULT_DEPARTMENT);
    profile.setEmployeeId(DEFAULT_EMPLOYEE_ID);
    profile.setAdminTier(AdminTier.SUPER);
    adminProfileRepository.save(profile);

    return user;
  }
}
