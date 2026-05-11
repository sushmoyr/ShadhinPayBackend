package pay.conflux.backend.provisioning.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;
import pay.conflux.backend.provisioning.usecase.GetVendorConfigUseCase;
import pay.conflux.backend.provisioning.usecase.VendorConfigDescriptor;

/**
 * Hot-path resolver used by {@code payment-core} to route a transaction at the per-business
 * vendor-configuration layer. Returns an opaque descriptor — plaintext credentials are resolved
 * separately, on demand, by {@link pay.conflux.backend.provisioning.usecase.CredentialsResolver}.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class GetVendorConfigUseCaseImpl implements GetVendorConfigUseCase {

  static final Duration CACHE_TTL = Duration.ofSeconds(300);
  static final String CUSTOM_REF_PREFIX = "vendor-credentials:";

  private final VendorConfigRepository vendorConfigRepository;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public VendorConfigDescriptor execute(UUID businessId, String vendor) {
    if (businessId == null) {
      throw new ValidationException("businessId is required");
    }
    Vendor vendorEnum = parseVendor(vendor);
    String cacheKey = ProvisioningCacheKeys.vendorConfig(businessId, vendorEnum.name());

    VendorConfigDescriptor cached = readFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    VendorConfig row =
        vendorConfigRepository
            .findByBusinessIdAndVendor(businessId, vendorEnum)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VendorConfig", businessId + ":" + vendorEnum.name()));

    VendorConfigDescriptor descriptor = toDescriptor(row);
    writeToCache(cacheKey, descriptor, businessId, vendorEnum.name());
    return descriptor;
  }

  static VendorConfigDescriptor toDescriptor(VendorConfig row) {
    if (row.getMode() == VendorMode.PARTNER) {
      return new VendorConfigDescriptor(row.getVendor().name(), "PARTNER", Map.of());
    }
    return new VendorConfigDescriptor(
        row.getVendor().name(),
        "CUSTOM",
        Map.of("ref", CUSTOM_REF_PREFIX + row.getId().toString()));
  }

  private VendorConfigDescriptor readFromCache(String cacheKey) {
    try {
      String raw = redisTemplate.opsForValue().get(cacheKey);
      if (raw == null) {
        return null;
      }
      return objectMapper.readValue(raw, VendorConfigDescriptor.class);
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("vendorconfig cache read failed key={}: {}", cacheKey, e.getMessage());
      return null;
    }
  }

  private void writeToCache(
      String cacheKey, VendorConfigDescriptor descriptor, UUID businessId, String vendor) {
    try {
      String json = objectMapper.writeValueAsString(descriptor);
      redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
      redisTemplate.opsForSet().add(ProvisioningCacheKeys.businessVendorIndex(businessId), vendor);
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("vendorconfig cache write failed key={}: {}", cacheKey, e.getMessage());
    }
  }

  private static Vendor parseVendor(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("vendor is required");
    }
    try {
      return Vendor.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown vendor: " + value);
    }
  }
}
