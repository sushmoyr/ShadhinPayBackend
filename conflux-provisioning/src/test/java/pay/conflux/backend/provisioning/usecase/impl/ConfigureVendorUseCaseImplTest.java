package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.dto.ConfigureVendorRequest;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;

@ExtendWith(MockitoExtension.class)
class ConfigureVendorUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private VendorConfigRepository vendorConfigRepository;
  @Mock private ProvisioningMapper mapper;
  @Mock private AesGcmCipher cipher;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private ConfigureVendorUseCaseImpl useCase;

  private UUID businessId;

  @BeforeEach
  void setUp() {
    businessId = UUID.randomUUID();
  }

  @Test
  void execute_partnerMode_storesNullCredentials() {
    when(businessRepository.existsById(businessId)).thenReturn(true);
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.BKASH))
        .thenReturn(Optional.empty());
    when(vendorConfigRepository.save(any(VendorConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(VendorConfig.class))).thenReturn(new VendorConfigDto());

    ConfigureVendorRequest request = new ConfigureVendorRequest("BKASH", "PARTNER", null);
    useCase.execute(businessId, request);

    ArgumentCaptor<VendorConfig> captor = ArgumentCaptor.forClass(VendorConfig.class);
    verify(vendorConfigRepository).save(captor.capture());
    VendorConfig persisted = captor.getValue();
    assertThat(persisted.getMode()).isEqualTo(VendorMode.PARTNER);
    assertThat(persisted.getCredentialsEncrypted()).isNull();
    verify(cipher, never()).encrypt(anyString(), anyString());
  }

  @Test
  void execute_customMode_storesEncryptedCiphertextNotPlaintext() {
    String plaintextSecret = "super-secret-app-key";
    String ciphertext = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA-encrypted-blob";
    when(businessRepository.existsById(businessId)).thenReturn(true);
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.NAGAD))
        .thenReturn(Optional.empty());
    when(cipher.encrypt(anyString(), eq(EncryptionPurpose.VENDOR_CREDENTIALS)))
        .thenReturn(ciphertext);
    when(vendorConfigRepository.save(any(VendorConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(mapper.toDto(any(VendorConfig.class))).thenReturn(new VendorConfigDto());

    ConfigureVendorRequest request =
        new ConfigureVendorRequest(
            "NAGAD", "CUSTOM", Map.of("appKey", plaintextSecret, "appSecret", "s"));
    useCase.execute(businessId, request);

    ArgumentCaptor<VendorConfig> captor = ArgumentCaptor.forClass(VendorConfig.class);
    verify(vendorConfigRepository).save(captor.capture());
    Map<String, String> stored = captor.getValue().getCredentialsEncrypted();

    assertThat(stored).containsOnlyKeys("ct");
    String storedCiphertext = stored.get("ct");
    assertThat(storedCiphertext).isEqualTo(ciphertext);
    assertThat(storedCiphertext).doesNotContain(plaintextSecret);
  }

  @Test
  void execute_customMode_emptyCredentials_rejected() {
    when(businessRepository.existsById(businessId)).thenReturn(true);
    ConfigureVendorRequest request = new ConfigureVendorRequest("BKASH", "CUSTOM", Map.of());
    assertThatThrownBy(() -> useCase.execute(businessId, request))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_unknownVendor_rejected() {
    when(businessRepository.existsById(businessId)).thenReturn(true);
    ConfigureVendorRequest request = new ConfigureVendorRequest("BOGUS", "PARTNER", null);
    assertThatThrownBy(() -> useCase.execute(businessId, request))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_unknownMode_rejected() {
    when(businessRepository.existsById(businessId)).thenReturn(true);
    ConfigureVendorRequest request = new ConfigureVendorRequest("BKASH", "BOGUS", null);
    assertThatThrownBy(() -> useCase.execute(businessId, request))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_businessMissing_throwsResourceNotFound() {
    UUID missing = UUID.randomUUID();
    ConfigureVendorRequest request = new ConfigureVendorRequest("BKASH", "PARTNER", null);
    assertThatThrownBy(() -> useCase.execute(missing, request))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
