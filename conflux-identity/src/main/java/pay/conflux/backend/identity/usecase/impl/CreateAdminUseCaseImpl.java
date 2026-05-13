package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.common.util.IdentifierDetector;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.CreateAdminUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class CreateAdminUseCaseImpl implements CreateAdminUseCase {

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminProfileMapper adminProfileMapper;

  @Override
  @Transactional
  public AdminProfileDto execute(CreateAdminRequest request) {
    pay.conflux.backend.common.util.IdentifierType commonType =
        IdentifierDetector.detect(request.identifier());
    IdentifierType identifierType = IdentifierType.valueOf(commonType.name());

    if (userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
        request.identifier(), identifierType)) {
      throw new DuplicateResourceException("Admin", "identifier", request.identifier());
    }

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier(request.identifier());
    user.setIdentifierType(identifierType);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setUserType(UserType.ADMIN);
    user.setStatus(UserStatus.ACTIVE);
    user.setMfaEnabled(false);
    userRepository.save(user);

    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(user.getId());
    profile.setDepartment(request.department());
    profile.setEmployeeId(request.employeeId());
    profile.setAdminTier(request.adminTier());
    adminProfileRepository.save(profile);

    log.info("Created admin {} with tier {}", user.getId(), profile.getAdminTier());
    return adminProfileMapper.toDto(user, profile);
  }
}
