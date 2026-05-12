package pay.conflux.backend.paymentcore.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.paymentcore.constant.PaymentCoreRoutes;
import pay.conflux.backend.paymentcore.dto.InitiatePaymentRestRequest;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.mapper.TransactionMapper;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;

@WebMvcTest(
    controllers = {MerchantPaymentControllerImpl.class, MerchantPaymentRefundControllerImpl.class})
@Import({GlobalExceptionHandler.class, TestSliceSecurityConfig.class})
class MerchantPaymentControllerImplTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private InitiatePaymentUseCase initiatePaymentUseCase;
  @MockitoBean private TransactionRepository transactionRepository;
  @MockitoBean private TransactionMapper transactionMapper;

  @MockitoBean
  private pay.conflux.backend.paymentcore.usecase.RefundPaymentUseCase refundPaymentUseCase;

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void post_happyPath_returns201WithEnvelope() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID trxId = UUID.randomUUID();
    InitiatePaymentRestRequest body = new InitiatePaymentRestRequest();
    body.setAmount(new BigDecimal("100.00"));
    body.setCurrency("BDT");
    body.setVendor("MOCK");
    body.setMerchantOrderReference("order-1");
    body.setCallbackUrl("https://merchant.example/return");
    body.setWebhookUrl("https://merchant.example/webhook");

    when(initiatePaymentUseCase.execute(any()))
        .thenReturn(new PaymentInitiationResult(trxId, "https://vendor/redirect", "PENDING"));

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS)
                .header(PaymentCoreRoutes.HEADER_IDEMPOTENCY_KEY, "idem-1")
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.transactionId").value(trxId.toString()))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.redirectUrl").value("https://vendor/redirect"))
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void post_missingIdempotencyKey_returns400() throws Exception {
    UUID businessId = UUID.randomUUID();
    InitiatePaymentRestRequest body = new InitiatePaymentRestRequest();
    body.setAmount(new BigDecimal("100.00"));
    body.setCurrency("BDT");
    body.setVendor("MOCK");
    body.setMerchantOrderReference("order-1");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS)
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void post_missingBusinessIdAttribute_returns400() throws Exception {
    InitiatePaymentRestRequest body = new InitiatePaymentRestRequest();
    body.setAmount(new BigDecimal("100.00"));
    body.setCurrency("BDT");
    body.setVendor("MOCK");
    body.setMerchantOrderReference("order-1");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS)
                .header(PaymentCoreRoutes.HEADER_IDEMPOTENCY_KEY, "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void post_invalidCurrency_returns400() throws Exception {
    UUID businessId = UUID.randomUUID();
    InitiatePaymentRestRequest body = new InitiatePaymentRestRequest();
    body.setAmount(new BigDecimal("100.00"));
    body.setCurrency("bdt");
    body.setVendor("MOCK");
    body.setMerchantOrderReference("order-1");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS)
                .header(PaymentCoreRoutes.HEADER_IDEMPOTENCY_KEY, "idem-1")
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void getById_tenantMismatch_returns404ToHideExistence() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID otherBusinessId = UUID.randomUUID();
    UUID trxId = UUID.randomUUID();

    Transaction tx = new Transaction();
    tx.setId(trxId);
    tx.setBusinessId(otherBusinessId);
    tx.setMerchantId(UUID.randomUUID());
    tx.setStatus(TransactionStatus.PENDING);
    tx.setMode(TransactionMode.PARTNER);
    tx.setVendor("MOCK");
    tx.setAmountValue(new BigDecimal("10"));
    tx.setAmountCurrency("BDT");
    tx.setMerchantOrderReference("o1");
    tx.setCreatedAt(LocalDateTime.now());

    when(transactionRepository.findById(trxId)).thenReturn(Optional.of(tx));

    mockMvc
        .perform(
            get(PaymentCoreRoutes.PAYMENTS + "/" + trxId)
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void refund_unknownTransaction_returns404() throws Exception {
    UUID trxId = UUID.randomUUID();
    when(transactionRepository.findById(trxId)).thenReturn(Optional.empty());

    pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest body =
        new pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest();
    body.setAmount(new BigDecimal("10.00"));
    body.setCurrency("BDT");
    body.setReason("x");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS + "/" + trxId + "/refund")
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void refund_tenantMismatch_returns403() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID otherBusinessId = UUID.randomUUID();
    UUID trxId = UUID.randomUUID();

    Transaction other = new Transaction();
    other.setId(trxId);
    other.setBusinessId(otherBusinessId);
    other.setStatus(TransactionStatus.COMPLETED);
    other.setMode(TransactionMode.PARTNER);
    other.setVendor("MOCK");
    other.setAmountValue(new BigDecimal("100.00"));
    other.setAmountCurrency("BDT");
    other.setMerchantOrderReference("o1");
    when(transactionRepository.findById(trxId)).thenReturn(Optional.of(other));

    pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest body =
        new pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest();
    body.setAmount(new BigDecimal("10.00"));
    body.setReason("x");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS + "/" + trxId + "/refund")
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void refund_happyPath_returns200WithEnvelope() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID trxId = UUID.randomUUID();
    UUID refundId = UUID.randomUUID();

    Transaction original = new Transaction();
    original.setId(trxId);
    original.setBusinessId(businessId);
    original.setMerchantId(UUID.randomUUID());
    original.setStatus(TransactionStatus.COMPLETED);
    original.setMode(TransactionMode.PARTNER);
    original.setVendor("MOCK");
    original.setAmountValue(new BigDecimal("100.00"));
    original.setAmountCurrency("BDT");
    original.setMerchantOrderReference("o1");
    when(transactionRepository.findById(trxId)).thenReturn(Optional.of(original));
    when(refundPaymentUseCase.execute(any()))
        .thenReturn(
            new pay.conflux.backend.paymentcore.usecase.RefundPaymentResult(
                refundId, trxId, "COMPLETED"));

    pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest body =
        new pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest();
    body.setAmount(new BigDecimal("50.00"));
    body.setCurrency("BDT");
    body.setReason("customer request");

    mockMvc
        .perform(
            post(PaymentCoreRoutes.PAYMENTS + "/" + trxId + "/refund")
                .requestAttr(PaymentCoreRoutes.HEADER_BUSINESS_ID, businessId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refundTransactionId").value(refundId.toString()))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.meta.success").value(true));
  }
}
