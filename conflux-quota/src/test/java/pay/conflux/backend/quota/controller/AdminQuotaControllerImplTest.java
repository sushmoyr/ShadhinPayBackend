package pay.conflux.backend.quota.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.quota.controller.dto.AdminQuotaUsageDto;
import pay.conflux.backend.quota.usecase.GetUsageUseCase;
import pay.conflux.backend.quota.usecase.QuotaUsageView;

class AdminQuotaControllerImplTest {

  private GetUsageUseCase getUsageUseCase;
  private AdminQuotaControllerImpl controller;

  @BeforeEach
  void setUp() {
    getUsageUseCase = mock(GetUsageUseCase.class);
    controller = new AdminQuotaControllerImpl(getUsageUseCase);
  }

  @Test
  void getQuota_returnsDto() {
    UUID merchantId = UUID.randomUUID();
    String period = "2026-05";

    when(getUsageUseCase.execute(merchantId, period)).thenReturn(new QuotaUsageView(10, 0, period));

    ResponseEntity<ApiResult<AdminQuotaUsageDto>> response =
        controller.getQuota(merchantId, period);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().merchantId()).isEqualTo(merchantId);
    assertThat(response.getBody().data().usedCount()).isEqualTo(10);
    assertThat(response.getBody().data().freeRemaining()).isEqualTo(0);
    assertThat(response.getBody().data().period()).isEqualTo(period);
  }
}
