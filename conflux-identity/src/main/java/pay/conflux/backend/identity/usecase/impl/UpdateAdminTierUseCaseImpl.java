package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.UpdateAdminTierUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class UpdateAdminTierUseCaseImpl implements UpdateAdminTierUseCase {

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final AdminProfileMapper adminProfileMapper;

  @Override
  @Transactional
  public AdminProfileDto execute(UUID targetUserId, AdminTier newTier) {
    AdminProfile profile =
        adminProfileRepository
            .findByUserId(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin", targetUserId));
    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

    AdminTier current = profile.getAdminTier();
    if (current == newTier) {
      return adminProfileMapper.toDto(user, profile);
    }

    if (current == AdminTier.SUPER && newTier != AdminTier.SUPER) {
      long activeSuperCount = adminProfileRepository.countActiveSuperAdmins();
      if (activeSuperCount <= 1) {
        throw new InvalidOperationStateException("Cannot demote the last SUPER admin");
      }
    }

    profile.setAdminTier(newTier);
    adminProfileRepository.save(profile);

    log.info("Updated admin {} tier from {} to {}", targetUserId, current, newTier);
    return adminProfileMapper.toDto(user, profile);
  }
}
