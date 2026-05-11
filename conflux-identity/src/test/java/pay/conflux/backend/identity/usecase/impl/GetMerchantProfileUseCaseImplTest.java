package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GetMerchantProfileUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private MerchantProfileMapper mapper;

  @InjectMocks private GetMerchantProfileUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setDeleted(false);
    MerchantProfile profile = new MerchantProfile();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    MerchantOnboardingDto dto =
        new MerchantOnboardingDto(userId, null, null, null, null, OnboardingStatus.REGISTERED);
    when(mapper.toDto(user, profile)).thenReturn(dto);

    MerchantOnboardingDto result = useCase.execute(userId);

    assertThat(result).isNotNull();
    assertThat(result.userId()).isEqualTo(userId);
  }

  @Test
  void execute_ThrowsWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId)).isInstanceOf(ResourceNotFoundException.class);
  }
}
