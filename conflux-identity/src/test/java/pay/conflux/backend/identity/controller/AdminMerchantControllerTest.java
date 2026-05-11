package pay.conflux.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.controller.impl.AdminMerchantControllerImpl;
import pay.conflux.backend.identity.dto.BlockUserRequest;
import pay.conflux.backend.identity.dto.RejectMerchantRequest;
import pay.conflux.backend.identity.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.identity.usecase.BlockUserUseCase;
import pay.conflux.backend.identity.usecase.GetMerchantProfilesUseCase;
import pay.conflux.backend.identity.usecase.RejectMerchantUseCase;
import pay.conflux.backend.identity.usecase.UnblockUserUseCase;
import pay.conflux.backend.identity.usecase.VerifyMerchantUseCase;

@WebMvcTest(AdminMerchantControllerImpl.class)
@Import({TestSliceSecurityConfig.class, GlobalExceptionHandler.class})
class AdminMerchantControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMerchantProfilesUseCase getMerchantProfilesUseCase;
  @MockitoBean private VerifyMerchantUseCase verifyMerchantUseCase;
  @MockitoBean private RejectMerchantUseCase rejectMerchantUseCase;
  @MockitoBean private BlockUserUseCase blockUserUseCase;
  @MockitoBean private UnblockUserUseCase unblockUserUseCase;

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void listMerchants_Success() throws Exception {
    when(getMerchantProfilesUseCase.execute(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get(IdentityRoutes.ADMIN_MERCHANTS)).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void verifyMerchant_Success() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(IdentityRoutes.ADMIN_MERCHANTS_VERIFY.replace("{id}", id.toString())))
        .andExpect(status().isOk());
    verify(verifyMerchantUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void rejectMerchant_Success() throws Exception {
    UUID id = UUID.randomUUID();
    RejectMerchantRequest request = new RejectMerchantRequest("Reason");
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_MERCHANTS_REJECT.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
    verify(rejectMerchantUseCase).execute(eq(id), any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void blockUser_Success() throws Exception {
    UUID id = UUID.randomUUID();
    BlockUserRequest request = new BlockUserRequest("Reason");
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_USERS_BLOCK.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
    verify(blockUserUseCase).execute(eq(id), any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void unblockUser_Success() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(IdentityRoutes.ADMIN_USERS_UNBLOCK.replace("{id}", id.toString())))
        .andExpect(status().isOk());
    verify(unblockUserUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void verifyMerchant_withoutAdminAuthority_returns403() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(IdentityRoutes.ADMIN_MERCHANTS_VERIFY.replace("{id}", id.toString())))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void rejectMerchant_withBlankReason_returns400() throws Exception {
    UUID id = UUID.randomUUID();
    RejectMerchantRequest request = new RejectMerchantRequest("");
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_MERCHANTS_REJECT.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.meta.success").value(false));
  }
}
