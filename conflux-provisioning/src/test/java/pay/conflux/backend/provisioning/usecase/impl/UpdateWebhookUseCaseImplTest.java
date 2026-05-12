package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
class UpdateWebhookUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @Mock private AesGcmCipher cipher;

  @InjectMocks private UpdateWebhookUseCaseImpl useCase;

  @Test
  void execute_httpUrl_rejected() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setWebhookSecretEncrypted("existing-cipher");
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    UpdateWebhookRequest request = new UpdateWebhookRequest("http://insecure.example.com/hook");

    assertThatThrownBy(() -> useCase.execute(businessId, request, false))
        .isInstanceOf(ValidationException.class);
    verify(businessRepository, never()).save(any());
  }

  @Test
  void execute_httpsUrl_noRotate_keepsExistingSecret() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setWebhookSecretEncrypted("existing-cipher");
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(Business.class))).thenReturn(new BusinessDto());

    useCase.execute(
        businessId, new UpdateWebhookRequest("https://merchant.example.com/hook"), false);

    ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
    verify(businessRepository).save(captor.capture());
    assertThat(captor.getValue().getWebhookUrl()).isEqualTo("https://merchant.example.com/hook");
    assertThat(captor.getValue().getWebhookSecretEncrypted()).isEqualTo("existing-cipher");
    verify(cipher, never()).encrypt(anyString(), anyString());
  }

  @Test
  void execute_rotate_replacesSecretWithFreshCiphertext() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setWebhookSecretEncrypted("old-cipher");
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(Business.class))).thenReturn(new BusinessDto());
    when(cipher.encrypt(anyString(), eq(EncryptionPurpose.WEBHOOK_SECRET)))
        .thenReturn("NEW-CIPHER");

    useCase.execute(
        businessId, new UpdateWebhookRequest("https://merchant.example.com/hook"), true);

    ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
    verify(businessRepository).save(captor.capture());
    assertThat(captor.getValue().getWebhookSecretEncrypted()).isEqualTo("NEW-CIPHER");
  }

  @Test
  void execute_initialSet_whenNoSecret_rotatesAutomatically() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setWebhookSecretEncrypted(null);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(Business.class))).thenReturn(new BusinessDto());
    when(cipher.encrypt(anyString(), anyString())).thenReturn("INIT-CIPHER");

    useCase.execute(
        businessId, new UpdateWebhookRequest("https://merchant.example.com/hook"), false);

    ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
    verify(businessRepository).save(captor.capture());
    assertThat(captor.getValue().getWebhookSecretEncrypted()).isEqualTo("INIT-CIPHER");
  }

  @Test
  void execute_missingBusiness_throwsResourceNotFound() {
    UUID missing = UUID.randomUUID();
    when(businessRepository.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(missing, new UpdateWebhookRequest("https://x"), false))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
