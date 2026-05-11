package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.identity.dto.MerchantSummaryDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GetMerchantProfilesUseCaseImplTest {

  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private GetMerchantProfilesUseCaseImpl useCase;

  @Test
  @SuppressWarnings("unchecked")
  void execute_Success() {
    UUID userId = UUID.randomUUID();
    MerchantProfile profile = new MerchantProfile();
    profile.setUserId(userId);
    profile.setFullName("Test Merchant");
    profile.setOnboardingStatus(OnboardingStatus.VERIFIED);
    profile.setCreatedAt(LocalDateTime.now());

    User user = new User();
    user.setIdentifier("test@example.com");

    Page<MerchantProfile> profilePage = new PageImpl<>(List.of(profile));
    when(merchantProfileRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(profilePage);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    Page<MerchantSummaryDto> result =
        useCase.execute(OnboardingStatus.VERIFIED, "Test", PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).fullName()).isEqualTo("Test Merchant");
    assertThat(result.getContent().get(0).identifier()).isEqualTo("test@example.com");
  }
}
