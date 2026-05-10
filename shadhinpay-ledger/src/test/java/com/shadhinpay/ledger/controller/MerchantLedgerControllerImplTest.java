package com.shadhinpay.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.common.security.AuthenticatedPrincipal;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.internal.ListJournalEntriesUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

@SpringBootTest(classes = MerchantLedgerControllerImplTest.TestConfig.class)
@AutoConfigureMockMvc
class MerchantLedgerControllerImplTest {

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    public MerchantLedgerControllerImpl merchantLedgerController(
        LedgerAccountRepository accountRepository,
        ListJournalEntriesUseCase listJournalEntriesUseCase,
        LedgerMapper mapper) {
      return new MerchantLedgerControllerImpl(accountRepository, listJournalEntriesUseCase, mapper);
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private LedgerAccountRepository accountRepository;
  @MockBean private ListJournalEntriesUseCase listJournalEntriesUseCase;
  @MockBean private LedgerMapper mapper;

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
  void shouldReturnMerchantBalance() throws Exception {
    UUID accountId = UUID.randomUUID();
    LedgerAccount account =
        new LedgerAccount(MERCHANT_ID, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(account, "id", accountId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        account, "balance", Money.of(500, "BDT"));

    when(accountRepository.findByOwnerIdAndCodeAndShardIdAndCurrency(
            MERCHANT_ID, "MERCHANT_PAYABLE", 0, "BDT"))
        .thenReturn(Optional.of(account));
    when(mapper.toBalanceDto(account))
        .thenReturn(new BalanceDto(accountId, "MERCHANT_PAYABLE", "LIABILITY", "BDT", "500.0000"));

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
  void shouldReturn401WhenNotAuthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/merchant/balance")
                .param("code", "MERCHANT_PAYABLE")
                .param("currency", "BDT")
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnJournalEntries() throws Exception {
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
  void merchantCannotPassMerchantIdParam() throws Exception {
    when(listJournalEntriesUseCase.execute(any(), eq(null), eq(null), eq(null), eq(MERCHANT_ID)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/v1/merchant/journal")
                .param("merchantId", UUID.randomUUID().toString())
                .param("page", "0")
                .param("size", "20")
                .with(authentication(merchantAuth()))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(listJournalEntriesUseCase).execute(any(), eq(null), eq(null), eq(null), eq(MERCHANT_ID));
  }
}
