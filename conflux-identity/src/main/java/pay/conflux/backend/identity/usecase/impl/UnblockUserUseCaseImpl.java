package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.UnblockUserUseCase;

@UseCase
@RequiredArgsConstructor
public class UnblockUserUseCaseImpl implements UnblockUserUseCase {

  private final UserRepository userRepository;

  @Override
  @Transactional
  public void execute(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User not found");
    }

    if (user.getStatus() == UserStatus.BLOCKED) {
      user.setStatus(UserStatus.ACTIVE);
      userRepository.save(user);
    }
  }
}
