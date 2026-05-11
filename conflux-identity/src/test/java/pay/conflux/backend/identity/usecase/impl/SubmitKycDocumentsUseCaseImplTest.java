package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SubmitKycDocumentsUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private MerchantProfileMapper mapper;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private SubmitKycDocumentsUseCaseImpl useCase;

  @Test
  void execute_Success() throws Exception {
    UUID userId = UUID.randomUUID();
    KycSubmissionRequest request = new KycSubmissionRequest("nidF", "nidB", "trade", "tin");

    User user = new User();
    user.setId(userId);
    user.setDeleted(false);

    MerchantProfile profile = new MerchantProfile();
    profile.setOnboardingStatus(OnboardingStatus.REGISTERED);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(objectMapper.writeValueAsString(request)).thenReturn("json");

    MerchantOnboardingDto dto =
        new MerchantOnboardingDto(
            userId, null, null, null, null, OnboardingStatus.PENDING_VERIFICATION);
    when(mapper.toDto(user, profile)).thenReturn(dto);

    MerchantOnboardingDto result = useCase.execute(userId, request);

    assertThat(result.onboardingStatus()).isEqualTo(OnboardingStatus.PENDING_VERIFICATION);
    assertThat(profile.getOnboardingStatus()).isEqualTo(OnboardingStatus.PENDING_VERIFICATION);
    assertThat(profile.getKycData()).isEqualTo("json");
    verify(merchantProfileRepository).save(profile);
  }

  @Test
  void execute_ThrowsWhenUserDeleted() {
    UUID userId = UUID.randomUUID();
    KycSubmissionRequest request = new KycSubmissionRequest("nidF", "nidB", "trade", "tin");

    User user = new User();
    user.setDeleted(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId, request))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_ThrowsWhenAlreadyPending() {
    UUID userId = UUID.randomUUID();
    KycSubmissionRequest request = new KycSubmissionRequest("nidF", "nidB", "trade", "tin");

    User user = new User();
    user.setDeleted(false);

    MerchantProfile profile = new MerchantProfile();
    profile.setOnboardingStatus(OnboardingStatus.PENDING_VERIFICATION);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> useCase.execute(userId, request))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("KYC documents have already been submitted");
  }
}
