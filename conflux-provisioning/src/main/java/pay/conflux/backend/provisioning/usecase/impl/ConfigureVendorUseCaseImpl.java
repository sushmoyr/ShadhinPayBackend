package pay.conflux.backend.provisioning.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
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
import pay.conflux.backend.provisioning.usecase.ConfigureVendorUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ConfigureVendorUseCaseImpl implements ConfigureVendorUseCase {

  private final BusinessRepository businessRepository;
  private final VendorConfigRepository vendorConfigRepository;
  private final ProvisioningMapper mapper;
  private final AesGcmCipher cipher;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public VendorConfigDto execute(UUID businessId, ConfigureVendorRequest request) {
    if (!businessRepository.existsById(businessId)) {
      throw new ResourceNotFoundException("Business", businessId);
    }

    Vendor vendor = parseEnum(Vendor.class, request.getVendor(), "vendor");
    VendorMode mode = parseEnum(VendorMode.class, request.getMode(), "mode");

    VendorConfig existing =
        vendorConfigRepository
            .findByBusinessIdAndVendor(businessId, vendor)
            .orElseGet(
                () -> {
                  VendorConfig vc = new VendorConfig();
                  vc.setBusinessId(businessId);
                  vc.setVendor(vendor);
                  return vc;
                });

    existing.setMode(mode);
    if (mode == VendorMode.PARTNER) {
      existing.setCredentialsEncrypted(null);
    } else {
      Map<String, String> creds = request.getCredentials();
      if (creds == null || creds.isEmpty()) {
        throw new ValidationException("credentials are required when mode=CUSTOM");
      }
      existing.setCredentialsEncrypted(Map.of("ct", encryptCredentials(creds)));
    }

    VendorConfig saved = vendorConfigRepository.save(existing);
    log.info("Configured vendor businessId={} vendor={} mode={}", businessId, vendor, mode);
    return mapper.toDto(saved);
  }

  private String encryptCredentials(Map<String, String> credentials) {
    try {
      String json = objectMapper.writeValueAsString(credentials);
      return cipher.encrypt(json, EncryptionPurpose.VENDOR_CREDENTIALS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize vendor credentials", e);
    }
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown " + field + ": " + value);
    }
  }
}
