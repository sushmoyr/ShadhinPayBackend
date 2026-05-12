package pay.conflux.backend.paymentcore.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.paymentcore.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackResult;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackUseCase;

@WebMvcTest(VendorCallbackControllerImpl.class)
@Import({GlobalExceptionHandler.class, TestSliceSecurityConfig.class})
class VendorCallbackControllerImplTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ProcessVendorCallbackUseCase processVendorCallbackUseCase;

  @Test
  void handleCallback_happyPath_returnsOkWithEnvelope() throws Exception {
    UUID trxId = UUID.randomUUID();
    when(processVendorCallbackUseCase.execute(eq("MOCK"), any()))
        .thenReturn(new ProcessVendorCallbackResult(trxId, "COMPLETED"));

    mockMvc
        .perform(
            post("/api/v1/payments/callback/MOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("mock_trx_id", "vendor-trx-1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionId").value(trxId.toString()))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  void handleCallback_emptyBody_stillDispatchesWithEmptyMap() throws Exception {
    UUID trxId = UUID.randomUUID();
    when(processVendorCallbackUseCase.execute(eq("MOCK"), any()))
        .thenReturn(new ProcessVendorCallbackResult(trxId, "PENDING"));

    mockMvc
        .perform(
            post("/api/v1/payments/callback/MOCK")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }
}
