package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;

@ExtendWith(MockitoExtension.class)
class CreateBusinessUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @Mock private AesGcmCipher cipher;
  @Mock private MerchantStatusPort merchantStatusPort;

  @InjectMocks private CreateBusinessUseCaseImpl useCase;

  private UUID merchantId;
  private CreateBusinessRequest request;

  @BeforeEach
  void setUp() {
    merchantId = UUID.randomUUID();
    request = new CreateBusinessRequest("Acme Corp", "Acme", "https://cdn/logo.png");
  }

  @Test
  void execute_whenMerchantNotActive_throwsResourceNotFound() {
    when(merchantStatusPort.isActive(merchantId)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(merchantId, request))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(businessRepository, never()).save(any());
  }

  @Test
  void execute_persistsBusinessWithEncryptedWebhookSecret() {
    when(merchantStatusPort.isActive(merchantId)).thenReturn(true);
    when(cipher.encrypt(anyString(), eq(EncryptionPurpose.WEBHOOK_SECRET)))
        .thenReturn("ENC:webhook");
    Business saved = new Business();
    saved.setId(UUID.randomUUID());
    when(businessRepository.save(any(Business.class))).thenReturn(saved);
    BusinessDto dto = new BusinessDto();
    when(mapper.toDto(saved)).thenReturn(dto);

    BusinessDto result = useCase.execute(merchantId, request);

    assertThat(result).isSameAs(dto);

    ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
    verify(businessRepository).save(captor.capture());
    Business persisted = captor.getValue();
    assertThat(persisted.getMerchantId()).isEqualTo(merchantId);
    assertThat(persisted.getName()).isEqualTo("Acme Corp");
    assertThat(persisted.getDisplayName()).isEqualTo("Acme");
    assertThat(persisted.getLogoUrl()).isEqualTo("https://cdn/logo.png");
    assertThat(persisted.getWebhookSecretEncrypted()).isEqualTo("ENC:webhook");
    assertThat(persisted.getWebhookSecretEncrypted()).doesNotContain("=");
  }

  @Test
  void execute_defaultsDisplayNameToName_whenBlank() {
    when(merchantStatusPort.isActive(merchantId)).thenReturn(true);
    when(cipher.encrypt(anyString(), anyString())).thenReturn("ENC:x");
    when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(Business.class))).thenReturn(new BusinessDto());

    CreateBusinessRequest noDisplay = new CreateBusinessRequest("Foo", null, null);
    useCase.execute(merchantId, noDisplay);

    ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
    verify(businessRepository).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isEqualTo("Foo");
  }
}
