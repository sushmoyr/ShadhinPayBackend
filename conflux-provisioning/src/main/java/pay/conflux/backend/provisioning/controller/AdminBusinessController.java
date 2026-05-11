package pay.conflux.backend.provisioning.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.ProvisioningRoutes;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;

@Tag(
    name = "Provisioning - Admin",
    description = "Administrative read-only and state-transition endpoints for businesses")
public interface AdminBusinessController {

  @GetMapping(ProvisioningRoutes.ADMIN_BUSINESSES)
  ResponseEntity<ApiResult<List<BusinessSummaryDto>>> listBusinesses(
      String search, UUID merchantId, BusinessStatus status, Pageable pageable);

  @GetMapping(ProvisioningRoutes.ADMIN_BUSINESS_BY_ID)
  ResponseEntity<ApiResult<BusinessDto>> getBusiness(UUID id);

  @PostMapping(ProvisioningRoutes.ADMIN_BUSINESS_DEACTIVATE)
  ResponseEntity<ApiResult<BusinessDto>> deactivateBusiness(UUID id);

  @PostMapping(ProvisioningRoutes.ADMIN_BUSINESS_ACTIVATE)
  ResponseEntity<ApiResult<BusinessDto>> activateBusiness(UUID id);
}
