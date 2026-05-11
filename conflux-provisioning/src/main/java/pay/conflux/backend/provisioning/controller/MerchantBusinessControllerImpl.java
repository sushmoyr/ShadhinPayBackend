package pay.conflux.backend.provisioning.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.dto.ConfigureVendorRequest;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.usecase.ConfigureVendorUseCase;
import pay.conflux.backend.provisioning.usecase.CreateBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.GetBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.ListBusinessesUseCase;
import pay.conflux.backend.provisioning.usecase.RevokeApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.RotateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.UpdateWebhookUseCase;
import pay.conflux.backend.provisioning.usecase.impl.BusinessOwnershipGuard;

@RestController
@RequiredArgsConstructor
public class MerchantBusinessControllerImpl implements MerchantBusinessController {

  private final CreateBusinessUseCase createBusinessUseCase;
  private final ListBusinessesUseCase listBusinessesUseCase;
  private final GetBusinessUseCase getBusinessUseCase;
  private final ConfigureVendorUseCase configureVendorUseCase;
  private final GenerateApiKeyUseCase generateApiKeyUseCase;
  private final RotateApiKeyUseCase rotateApiKeyUseCase;
  private final RevokeApiKeyUseCase revokeApiKeyUseCase;
  private final UpdateWebhookUseCase updateWebhookUseCase;
  private final BusinessOwnershipGuard ownershipGuard;

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<BusinessDto>> createBusiness(
      @RequestBody @Valid CreateBusinessRequest request) {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new UnauthorizedException("No merchant context found"));
    return ApiResult.created(createBusinessUseCase.execute(merchantId, request));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<List<BusinessSummaryDto>>> listBusinesses() {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new UnauthorizedException("No merchant context found"));
    return ApiResult.ok(listBusinessesUseCase.execute(merchantId));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<BusinessDto>> getBusiness(@PathVariable UUID id) {
    ownershipGuard.requireOwned(id);
    return ApiResult.ok(getBusinessUseCase.execute(id));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<VendorConfigDto>> configureVendor(
      @PathVariable UUID id, @RequestBody @Valid ConfigureVendorRequest request) {
    ownershipGuard.requireOwned(id);
    return ApiResult.ok(configureVendorUseCase.execute(id, request));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<ApiKeyDto>> generateApiKey(
      @PathVariable UUID id, @RequestBody @Valid GenerateApiKeyRequest request) {
    ownershipGuard.requireOwned(id);
    return ApiResult.created(generateApiKeyUseCase.execute(id, request));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<ApiKeyDto>> rotateApiKey(
      @PathVariable UUID id, @PathVariable UUID keyId) {
    ownershipGuard.requireOwned(id);
    return ApiResult.created(rotateApiKeyUseCase.execute(id, keyId));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<Void>> revokeApiKey(
      @PathVariable UUID id, @PathVariable UUID keyId) {
    ownershipGuard.requireOwned(id);
    revokeApiKeyUseCase.execute(id, keyId);
    return ApiResult.ok();
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<BusinessDto>> updateWebhook(
      @PathVariable UUID id, @RequestBody @Valid UpdateWebhookRequest request) {
    ownershipGuard.requireOwned(id);
    return ApiResult.ok(updateWebhookUseCase.execute(id, request, true));
  }
}
