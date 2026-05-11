package pay.conflux.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.controller.impl.MfaControllerImpl;
import pay.conflux.backend.identity.dto.MfaDisableRequest;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.dto.MfaVerifyRequest;
import pay.conflux.backend.identity.usecase.DisableMfaUseCase;
import pay.conflux.backend.identity.usecase.EnableMfaUseCase;
import pay.conflux.backend.identity.usecase.VerifyMfaUseCase;

@WebMvcTest(MfaControllerImpl.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MfaControllerImplTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private EnableMfaUseCase enableMfaUseCase;
  @MockitoBean private VerifyMfaUseCase verifyMfaUseCase;
  @MockitoBean private DisableMfaUseCase disableMfaUseCase;

  @Test
  void enable_success() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaEnableResponse response =
        new MfaEnableResponse("SECRET123", "otpauth://totp/test", "data:image/png;base64,abc");

    when(enableMfaUseCase.execute(userId)).thenReturn(response);

    mockMvc
        .perform(post(IdentityRoutes.AUTH_MFA_ENABLE).header("X-User-Id", userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.secret").value("SECRET123"))
        .andExpect(jsonPath("$.data.provisioningUri").value("otpauth://totp/test"))
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  void enable_whenAlreadyEnabled_returns422() throws Exception {
    UUID userId = UUID.randomUUID();
    when(enableMfaUseCase.execute(userId))
        .thenThrow(new InvalidOperationStateException("MFA is already enabled"));

    mockMvc
        .perform(post(IdentityRoutes.AUTH_MFA_ENABLE).header("X-User-Id", userId.toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.meta.success").value(false));
  }

  @Test
  void verify_validCode_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaVerifyRequest request = new MfaVerifyRequest("123456");

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_VERIFY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  void verify_invalidCode_returns401() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaVerifyRequest request = new MfaVerifyRequest("000000");

    doThrow(new UnauthorizedException("Invalid MFA code"))
        .when(verifyMfaUseCase)
        .execute(userId, "000000");

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_VERIFY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.meta.success").value(false));
  }

  @Test
  void verify_missingCode_returns400() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_VERIFY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void disable_validPassword_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaDisableRequest request = new MfaDisableRequest("password123");

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_DISABLE)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  void disable_wrongPassword_returns401() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaDisableRequest request = new MfaDisableRequest("wrong");

    doThrow(new UnauthorizedException("Invalid password"))
        .when(disableMfaUseCase)
        .execute(userId, "wrong");

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_DISABLE)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.meta.success").value(false));
  }

  @Test
  void disable_whenNotEnabled_returns422() throws Exception {
    UUID userId = UUID.randomUUID();
    MfaDisableRequest request = new MfaDisableRequest("password");

    doThrow(new InvalidOperationStateException("MFA is not enabled"))
        .when(disableMfaUseCase)
        .execute(eq(userId), any());

    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_MFA_DISABLE)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.meta.success").value(false));
  }
}
