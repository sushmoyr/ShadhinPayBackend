package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pay.conflux.backend.common.error.ForbiddenException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

/**
 * Tenant-isolation guard for every merchant-facing controller method that takes a {@code {id}} path
 * variable. Loads the business, asserts it is owned by the currently authenticated merchant, and
 * returns the entity for downstream use. Centralising the check here means a new controller
 * endpoint cannot accidentally skip the check by forgetting to filter on merchantId.
 */
@Component
@RequiredArgsConstructor
public class BusinessOwnershipGuard {

  private final BusinessRepository businessRepository;

  /**
   * Asserts that the current authenticated merchant owns the given business.
   *
   * @throws UnauthorizedException if no merchant principal is bound to the request
   * @throws ResourceNotFoundException if the business does not exist or is soft-deleted
   * @throws ForbiddenException if the business belongs to a different merchant
   */
  public Business requireOwned(UUID businessId) {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new UnauthorizedException("No merchant context found"));

    Business business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    if (business.isDeleted()) {
      throw new ResourceNotFoundException("Business", businessId);
    }
    if (!merchantId.equals(business.getMerchantId())) {
      throw new ForbiddenException("Business does not belong to the authenticated merchant");
    }
    return business;
  }
}
