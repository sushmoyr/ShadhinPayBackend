package com.shadhinpay.quota.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.common.security.SecurityUtils;
import com.shadhinpay.quota.controller.dto.QuotaUsageDto;
import com.shadhinpay.quota.usecase.GetUsageUseCase;
import com.shadhinpay.quota.usecase.QuotaUsageView;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MerchantQuotaControllerImplTest {

  private GetUsageUseCase getUsageUseCase;
  private MerchantQuotaControllerImpl controller;
  private MockedStatic<SecurityUtils> securityUtilsMock;

  @BeforeEach
  void setUp() {
    getUsageUseCase = mock(GetUsageUseCase.class);
    controller = new MerchantQuotaControllerImpl(getUsageUseCase, Clock.systemUTC());
    securityUtilsMock = mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    securityUtilsMock.close();
  }

  @Test
  void getUsage_returnsDto_whenAuthenticated() {
    UUID merchantId = UUID.randomUUID();
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));

    when(getUsageUseCase.execute(eq(merchantId), anyString()))
        .thenReturn(new QuotaUsageView(5, 5, "2026-05"));

    ResponseEntity<ApiResult<QuotaUsageDto>> response = controller.getUsage();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().usedCount()).isEqualTo(5);
    assertThat(response.getBody().data().freeRemaining()).isEqualTo(5);
    assertThat(response.getBody().data().period()).isEqualTo("2026-05");
  }

  @Test
  void getUsage_throwsUnauthorized_whenNotAuthenticated() {
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.getUsage())
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("No merchant context found");
  }
}
