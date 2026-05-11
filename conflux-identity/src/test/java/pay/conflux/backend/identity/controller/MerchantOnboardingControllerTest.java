package pay.conflux.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.controller.impl.MerchantOnboardingControllerImpl;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.identity.usecase.GetMerchantProfileUseCase;
import pay.conflux.backend.identity.usecase.SubmitKycDocumentsUseCase;

@WebMvcTest(MerchantOnboardingControllerImpl.class)
@Import(TestSliceSecurityConfig.class)
class MerchantOnboardingControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private SubmitKycDocumentsUseCase submitKycDocumentsUseCase;
  @MockitoBean private GetMerchantProfileUseCase getMerchantProfileUseCase;

  @Test
  @WithMockUser
  void submitKyc_Success() throws Exception {
    UUID userId = UUID.randomUUID();
    KycSubmissionRequest request =
        new KycSubmissionRequest("http://nidF.com", "http://nidB.com", "http://trade.com", null);

    MerchantOnboardingDto dto =
        new MerchantOnboardingDto(
            userId, "id", null, null, "Name", OnboardingStatus.PENDING_VERIFICATION);
    when(submitKycDocumentsUseCase.execute(eq(userId), any())).thenReturn(dto);

    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_KYC)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onboardingStatus").value("PENDING_VERIFICATION"));
  }

  @Test
  @WithMockUser
  void getMe_Success() throws Exception {
    UUID userId = UUID.randomUUID();
    MerchantOnboardingDto dto =
        new MerchantOnboardingDto(userId, "id", null, null, "Name", OnboardingStatus.REGISTERED);
    when(getMerchantProfileUseCase.execute(userId)).thenReturn(dto);

    mockMvc
        .perform(get(IdentityRoutes.MERCHANT_ME).header("X-User-Id", userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(userId.toString()));
  }
}
