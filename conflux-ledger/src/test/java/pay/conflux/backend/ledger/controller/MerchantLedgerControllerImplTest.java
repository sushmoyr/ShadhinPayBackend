package pay.conflux.backend.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;
import pay.conflux.backend.ledger.usecase.internal.ListJournalEntriesUseCase;

@SpringBootTest(classes = MerchantLedgerControllerImplTest.TestConfig.class)
@AutoConfigureMockMvc
class MerchantLedgerControllerImplTest {

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    public MerchantLedgerControllerImpl merchantLedgerController(
        GetAccountBalanceUseCase getAccountBalanceUseCase,
        ListJournalEntriesUseCase listJournalEntriesUseCase) {
      return new MerchantLedgerControllerImpl(getAccountBalanceUseCase, listJournalEntriesUseCase);
    }

    @Bean
    public pay.conflux.backend.common.handler.GlobalExceptionHandler globalExceptionHandler() {
      return new pay.conflux.backend.common.handler.GlobalExceptionHandler();
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private GetAccountBalanceUseCase getAccountBalanceUseCase;
  @MockBean private ListJournalEntriesUseCase listJournalEntriesUseCase;

  private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private UsernamePasswordAuthenticationToken merchantAuth() {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            UUID.randomUUID(),
            AuthenticatedPrincipal.UserType.MERCHANT,
            MERCHANT_ID,
            UUID.randomUUID(),
            AuthenticatedPrincipal.Environment.TEST);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority("MERCHANT")));
  }

  @Test
  void getBalance_returnsMerchantBalanceFromUseCase() throws Exception {
    when(getAccountBalanceUseCase.execute(MERCHANT_ID, "MERCHANT_PAYABLE"))
        .thenReturn(Money.of(500, "BDT"));

    mockMvc
        .perform(
            get("/api/v1/merchant/balance")
                .param("code", "MERCHANT_PAYABLE")
                .param("currency", "BDT")
                .with(authentication(merchantAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.amount").value("500.0000"))
        .andExpect(jsonPath("$.data.accountCode").value("MERCHANT_PAYABLE"))
        .andExpect(jsonPath("$.data.currency").value("BDT"));
  }

  @Test
  void getBalance_returns404WhenUseCaseThrowsResourceNotFound() throws Exception {
    when(getAccountBalanceUseCase.execute(MERCHANT_ID, "MERCHANT_PAYABLE"))
        .thenThrow(new ResourceNotFoundException("Merchant account", MERCHANT_ID));

    mockMvc
        .perform(get("/api/v1/merchant/balance").with(authentication(merchantAuth())).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.meta.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void getBalance_unauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/merchant/balance").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listJournal_returnsCallerScopedEntries() throws Exception {
    PageImpl<JournalEntryDto> page =
        new PageImpl<>(
            List.of(
                new JournalEntryDto(
                    UUID.randomUUID(),
                    "PAYMENT",
                    "src-1",
                    "desc",
                    Instant.now(),
                    null,
                    List.of())));

    when(listJournalEntriesUseCase.execute(any(), eq(null), eq(null), eq(null), eq(MERCHANT_ID)))
        .thenReturn(page);

    mockMvc
        .perform(
            get("/api/v1/merchant/journal")
                .param("page", "0")
                .param("size", "20")
                .with(authentication(merchantAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].sourceType").value("PAYMENT"))
        .andExpect(jsonPath("$.pagination").exists());
  }

  @Test
  void listJournal_ignoresForgedMerchantIdParam() throws Exception {
    when(listJournalEntriesUseCase.execute(any(), eq(null), eq(null), eq(null), eq(MERCHANT_ID)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/v1/merchant/journal")
                .param("merchantId", UUID.randomUUID().toString())
                .with(authentication(merchantAuth()))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(listJournalEntriesUseCase).execute(any(), eq(null), eq(null), eq(null), eq(MERCHANT_ID));
  }
}
