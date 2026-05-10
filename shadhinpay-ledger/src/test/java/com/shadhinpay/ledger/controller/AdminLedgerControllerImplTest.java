package com.shadhinpay.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.common.security.AuthenticatedPrincipal;
import com.shadhinpay.ledger.dto.AccountIntegrityRecord;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.dto.TrialBalanceReportDto;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.internal.ListJournalEntriesUseCase;
import com.shadhinpay.ledger.usecase.internal.VerifyTrialBalanceUseCase;
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

@SpringBootTest(classes = AdminLedgerControllerImplTest.TestConfig.class)
@AutoConfigureMockMvc
class AdminLedgerControllerImplTest {

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    public AdminLedgerControllerImpl adminLedgerController(
        ListJournalEntriesUseCase listJournalEntriesUseCase,
        VerifyTrialBalanceUseCase verifyTrialBalanceUseCase,
        LedgerAccountRepository accountRepository,
        LedgerMapper mapper) {
      return new AdminLedgerControllerImpl(
          listJournalEntriesUseCase, verifyTrialBalanceUseCase, accountRepository, mapper);
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private ListJournalEntriesUseCase listJournalEntriesUseCase;
  @MockBean private VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  @MockBean private LedgerAccountRepository accountRepository;
  @MockBean private LedgerMapper mapper;

  private UsernamePasswordAuthenticationToken adminViewerAuth() {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            UUID.randomUUID(),
            AuthenticatedPrincipal.UserType.ADMIN,
            null,
            null,
            AuthenticatedPrincipal.Environment.TEST);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority("ADMIN_VIEWER")));
  }

  private UsernamePasswordAuthenticationToken adminManagerAuth() {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            UUID.randomUUID(),
            AuthenticatedPrincipal.UserType.ADMIN,
            null,
            null,
            AuthenticatedPrincipal.Environment.TEST);
    return new UsernamePasswordAuthenticationToken(
        principal,
        null,
        List.of(
            new SimpleGrantedAuthority("ADMIN_VIEWER"),
            new SimpleGrantedAuthority("ADMIN_MANAGER")));
  }

  @Test
  void shouldReturnJournalEntriesWithFilters() throws Exception {
    when(listJournalEntriesUseCase.execute(any(), any(), any(), any(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new JournalEntryDto(
                        UUID.randomUUID(),
                        "PAYMENT",
                        "src-1",
                        "desc",
                        Instant.now(),
                        null,
                        List.of()))));

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/journal")
                .param("page", "0")
                .param("size", "20")
                .param("sourceType", "PAYMENT")
                .param("startDate", "2025-01-01T00:00:00Z")
                .param("endDate", "2025-01-31T23:59:59Z")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].sourceType").value("PAYMENT"));
  }

  @Test
  void shouldReturnAggregatedBalance() throws Exception {
    LedgerAccount shard0 = new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(
        shard0, "balance", Money.of(100, "BDT"));

    LedgerAccount shard1 = new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", 1, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(
        shard1, "balance", Money.of(200, "BDT"));

    when(accountRepository.findByCodeAndCurrency("ESCROW", "BDT"))
        .thenReturn(List.of(shard0, shard1));

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/balance/ESCROW")
                .param("currency", "BDT")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.amount").value("300.0000"))
        .andExpect(jsonPath("$.data.accountCode").value("ESCROW"));
  }

  @Test
  void shouldReturnTrialBalanceWithManagerRole() throws Exception {
    TrialBalanceReportDto cleanReport = new TrialBalanceReportDto(true, List.of(), Instant.now());
    when(verifyTrialBalanceUseCase.execute()).thenReturn(cleanReport);

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/trial-balance")
                .with(authentication(adminManagerAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.globalSumZero").value(true))
        .andExpect(jsonPath("$.data.balanceMismatches").isEmpty());
  }

  @Test
  void shouldReturnTrialBalanceWithMismatches() throws Exception {
    AccountIntegrityRecord mismatch =
        new AccountIntegrityRecord(
            UUID.randomUUID(), "ESCROW", null, "100.0000", "90.0000", "-10.0000");
    TrialBalanceReportDto report =
        new TrialBalanceReportDto(false, List.of(mismatch), Instant.now());
    when(verifyTrialBalanceUseCase.execute()).thenReturn(report);

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/trial-balance")
                .with(authentication(adminManagerAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.globalSumZero").value(false))
        .andExpect(jsonPath("$.data.balanceMismatches[0].accountCode").value("ESCROW"))
        .andExpect(jsonPath("$.data.balanceMismatches[0].delta").value("-10.0000"));
  }

  @Test
  void shouldRejectTrialBalanceWithoutManagerRole() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/ledger/trial-balance")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturn404ForUnknownAccountBalance() throws Exception {
    when(accountRepository.findByCodeAndCurrency("UNKNOWN", "BDT")).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/balance/UNKNOWN")
                .param("currency", "BDT")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }
}
