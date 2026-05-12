package pay.conflux.backend.provisioning.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.spec.BusinessSpec;
import pay.conflux.backend.provisioning.usecase.GetBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.SearchBusinessesUseCase;
import pay.conflux.backend.provisioning.usecase.SetBusinessStatusUseCase;

@RestController
@RequiredArgsConstructor
public class AdminBusinessControllerImpl implements AdminBusinessController {

  private final SearchBusinessesUseCase searchBusinessesUseCase;
  private final GetBusinessUseCase getBusinessUseCase;
  private final SetBusinessStatusUseCase setBusinessStatusUseCase;

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<List<BusinessSummaryDto>>> listBusinesses(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID merchantId,
      @RequestParam(required = false) BusinessStatus status,
      Pageable pageable) {
    BusinessSpec spec = new BusinessSpec(search, merchantId, status);
    Page<BusinessSummaryDto> page = searchBusinessesUseCase.execute(spec, pageable);
    return ApiResult.ok(page);
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<BusinessDto>> getBusiness(@PathVariable UUID id) {
    return ApiResult.ok(getBusinessUseCase.execute(id));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<BusinessDto>> deactivateBusiness(@PathVariable UUID id) {
    return ApiResult.ok(setBusinessStatusUseCase.execute(id, BusinessStatus.INACTIVE));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<BusinessDto>> activateBusiness(@PathVariable UUID id) {
    return ApiResult.ok(setBusinessStatusUseCase.execute(id, BusinessStatus.ACTIVE));
  }
}
