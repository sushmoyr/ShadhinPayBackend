package com.shadhinpay.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.dto.RejectMerchantRequest;
import com.shadhinpay.identity.usecase.BlockUserUseCase;
import com.shadhinpay.identity.usecase.GetMerchantProfilesUseCase;
import com.shadhinpay.identity.usecase.RejectMerchantUseCase;
import com.shadhinpay.identity.usecase.UnblockUserUseCase;
import com.shadhinpay.identity.usecase.VerifyMerchantUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminMerchantController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminMerchantControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMerchantProfilesUseCase getMerchantProfilesUseCase;
  @MockitoBean private VerifyMerchantUseCase verifyMerchantUseCase;
  @MockitoBean private RejectMerchantUseCase rejectMerchantUseCase;
  @MockitoBean private BlockUserUseCase blockUserUseCase;
  @MockitoBean private UnblockUserUseCase unblockUserUseCase;

  @Test
  void listMerchants_Success() throws Exception {
    when(getMerchantProfilesUseCase.execute(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get(IdentityRoutes.ADMIN_MERCHANTS)).andExpect(status().isOk());
  }

  @Test
  void verifyMerchant_Success() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(IdentityRoutes.ADMIN_MERCHANTS_VERIFY.replace("{id}", id.toString())))
        .andExpect(status().isOk());
    verify(verifyMerchantUseCase).execute(id);
  }

  @Test
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
  void unblockUser_Success() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(IdentityRoutes.ADMIN_USERS_UNBLOCK.replace("{id}", id.toString())))
        .andExpect(status().isOk());
    verify(unblockUserUseCase).execute(id);
  }
}
