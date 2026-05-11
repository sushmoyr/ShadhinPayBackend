package pay.conflux.backend.ledger.controller;

import static org.mockito.ArgumentMatchers.any;
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
import pay.conflux.backend.ledger.dto.AccountIntegrityRecord;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;
import pay.conflux.backend.ledger.usecase.internal.ListJournalEntriesUseCase;
import pay.conflux.backend.ledger.usecase.internal.VerifyTrialBalanceUseCase;

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
        GetAccountBalanceUseCase getAccountBalanceUseCase) {
      return new AdminLedgerControllerImpl(
          listJournalEntriesUseCase, verifyTrialBalanceUseCase, getAccountBalanceUseCase);
    }

    @Bean
    public pay.conflux.backend.common.handler.GlobalExceptionHandler globalExceptionHandler() {
      return new pay.conflux.backend.common.handler.GlobalExceptionHandler();
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private ListJournalEntriesUseCase listJournalEntriesUseCase;
  @MockBean private VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  @MockBean private GetAccountBalanceUseCase getAccountBalanceUseCase;

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
  void listJournal_returnsFilteredEntries() throws Exception {
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
  void getBalance_returnsAggregatedSystemBalance() throws Exception {
    when(getAccountBalanceUseCase.execute(null, "ESCROW")).thenReturn(Money.of(300, "BDT"));

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/balance/ESCROW")
                .param("currency", "BDT")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.amount").value("300.0000"))
        .andExpect(jsonPath("$.data.accountCode").value("ESCROW"))
        .andExpect(jsonPath("$.data.accountType").value("CLEARING"));
  }

  @Test
  void getBalance_returns404WhenUseCaseThrowsResourceNotFound() throws Exception {
    when(getAccountBalanceUseCase.execute(null, "UNKNOWN"))
        .thenThrow(new ResourceNotFoundException("System account", "UNKNOWN"));

    mockMvc
        .perform(
            get("/api/v1/admin/ledger/balance/UNKNOWN")
                .param("currency", "BDT")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.meta.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void trialBalance_managerSeesCleanReport() throws Exception {
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
  void trialBalance_managerSeesMismatches() throws Exception {
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
  void trialBalance_viewerRoleRejected() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/ledger/trial-balance")
                .with(authentication(adminViewerAuth()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }
}
