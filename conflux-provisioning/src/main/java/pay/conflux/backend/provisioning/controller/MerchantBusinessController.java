package pay.conflux.backend.provisioning.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.provisioning.constant.ProvisioningRoutes;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.dto.ConfigureVendorRequest;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;

@Tag(
    name = "Provisioning - Merchant",
    description = "Merchant-owned business, vendor, API-key and webhook configuration endpoints")
public interface MerchantBusinessController {

  @PostMapping(ProvisioningRoutes.MERCHANT_BUSINESSES)
  ResponseEntity<ApiResult<BusinessDto>> createBusiness(CreateBusinessRequest request);

  @GetMapping(ProvisioningRoutes.MERCHANT_BUSINESSES)
  ResponseEntity<ApiResult<List<BusinessSummaryDto>>> listBusinesses();

  @GetMapping(ProvisioningRoutes.MERCHANT_BUSINESS_BY_ID)
  ResponseEntity<ApiResult<BusinessDto>> getBusiness(UUID id);

  @PostMapping(ProvisioningRoutes.MERCHANT_BUSINESS_VENDORS)
  ResponseEntity<ApiResult<VendorConfigDto>> configureVendor(
      UUID id, ConfigureVendorRequest request);

  @PostMapping(ProvisioningRoutes.MERCHANT_BUSINESS_APIKEYS)
  ResponseEntity<ApiResult<ApiKeyDto>> generateApiKey(UUID id, GenerateApiKeyRequest request);

  @PostMapping(ProvisioningRoutes.MERCHANT_BUSINESS_APIKEY_ROTATE)
  ResponseEntity<ApiResult<ApiKeyDto>> rotateApiKey(UUID id, UUID keyId);

  @DeleteMapping(ProvisioningRoutes.MERCHANT_BUSINESS_APIKEY_BY_ID)
  ResponseEntity<ApiResult<Void>> revokeApiKey(UUID id, UUID keyId);

  @PutMapping(ProvisioningRoutes.MERCHANT_BUSINESS_WEBHOOK)
  ResponseEntity<ApiResult<BusinessDto>> updateWebhook(UUID id, UpdateWebhookRequest request);
}
