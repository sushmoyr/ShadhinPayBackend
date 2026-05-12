package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import pay.conflux.backend.common.error.ForbiddenException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

class BusinessOwnershipGuardTest {

  private BusinessRepository businessRepository;
  private BusinessOwnershipGuard guard;
  private MockedStatic<SecurityUtils> securityUtilsMock;

  @BeforeEach
  void setUp() {
    businessRepository = mock(BusinessRepository.class);
    guard = new BusinessOwnershipGuard(businessRepository);
    securityUtilsMock = mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    securityUtilsMock.close();
  }

  @Test
  void requireOwned_authenticatedMerchantOwnsBusiness_returnsEntity() {
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(merchantId);
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    Business resolved = guard.requireOwned(businessId);

    org.assertj.core.api.Assertions.assertThat(resolved).isSameAs(business);
  }

  @Test
  void requireOwned_noMerchantContext_throwsUnauthorized() {
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.empty());
    assertThatThrownBy(() -> guard.requireOwned(UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void requireOwned_businessMissing_throwsResourceNotFound() {
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));
    when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guard.requireOwned(businessId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void requireOwned_businessSoftDeleted_throwsResourceNotFound() {
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(merchantId);
    business.setDeleted(true);
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    assertThatThrownBy(() -> guard.requireOwned(businessId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void requireOwned_differentOwner_throwsForbidden() {
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(UUID.randomUUID());
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    assertThatThrownBy(() -> guard.requireOwned(businessId)).isInstanceOf(ForbiddenException.class);
  }
}
