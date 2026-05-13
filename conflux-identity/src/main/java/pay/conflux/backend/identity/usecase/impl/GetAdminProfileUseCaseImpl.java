package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.GetAdminProfileUseCase;

@UseCase
@RequiredArgsConstructor
public class GetAdminProfileUseCaseImpl implements GetAdminProfileUseCase {

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final AdminProfileMapper adminProfileMapper;

  @Override
  @Transactional(readOnly = true)
  public AdminProfileDto execute(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", userId));
    AdminProfile profile =
        adminProfileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("AdminProfile", userId));
    return adminProfileMapper.toDto(user, profile);
  }
}
