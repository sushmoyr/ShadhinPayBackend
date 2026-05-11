package pay.conflux.backend.risk.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.risk.dto.*;
import pay.conflux.backend.risk.entity.BlacklistType;
import pay.conflux.backend.risk.entity.RuleAction;
import pay.conflux.backend.risk.entity.TrustLevel;
import pay.conflux.backend.risk.usecase.internal.*;

@WebMvcTest(AdminRiskControllerImpl.class)
class AdminRiskControllerTest {

  @org.springframework.boot.SpringBootConfiguration
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.springframework.context.annotation.ComponentScan(
      basePackageClasses = AdminRiskController.class)
  static class TestConfig {}

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ListRiskRulesUseCase listRiskRulesUseCase;
  @MockitoBean private CreateRiskRuleUseCase createRiskRuleUseCase;
  @MockitoBean private UpdateRiskRuleUseCase updateRiskRuleUseCase;
  @MockitoBean private DisableRiskRuleUseCase disableRiskRuleUseCase;
  @MockitoBean private ListBlacklistUseCase listBlacklistUseCase;
  @MockitoBean private AddBlacklistEntryUseCase addBlacklistEntryUseCase;
  @MockitoBean private RemoveBlacklistEntryUseCase removeBlacklistEntryUseCase;
  @MockitoBean private GetMerchantRiskProfileUseCase getMerchantRiskProfileUseCase;
  @MockitoBean private UpsertMerchantRiskProfileUseCase upsertMerchantRiskProfileUseCase;
  @MockitoBean private ListPendingCasesUseCase listPendingCasesUseCase;
  @MockitoBean private ApproveRiskCaseUseCase approveRiskCaseUseCase;
  @MockitoBean private RejectRiskCaseUseCase rejectRiskCaseUseCase;

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldCreateRule() throws Exception {
    CreateRiskRuleRequest request = new CreateRiskRuleRequest("name", "expr", 10, RuleAction.BLOCK);

    mockMvc
        .perform(
            post("/api/v1/admin/risk/rules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    verify(createRiskRuleUseCase).execute(any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldListRules() throws Exception {
    when(listRiskRulesUseCase.execute(any()))
        .thenReturn(org.springframework.data.domain.Page.empty());
    mockMvc.perform(get("/api/v1/admin/risk/rules")).andExpect(status().isOk());
    verify(listRiskRulesUseCase).execute(any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldUpdateRule() throws Exception {
    UUID id = UUID.randomUUID();
    UpdateRiskRuleRequest request = new UpdateRiskRuleRequest("expr", 20, RuleAction.FLAG);
    when(updateRiskRuleUseCase.execute(eq(id), any())).thenReturn(mock(RiskRuleDto.class));

    mockMvc
        .perform(
            put("/api/v1/admin/risk/rules/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(updateRiskRuleUseCase).execute(eq(id), any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldDisableRule() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(delete("/api/v1/admin/risk/rules/" + id).with(csrf()))
        .andExpect(status().isOk());
    verify(disableRiskRuleUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldAddBlacklistEntry() throws Exception {
    AddBlacklistEntryRequest request =
        new AddBlacklistEntryRequest(
            BlacklistType.IP, "1.1.1.1", "reason", Instant.now().plusSeconds(3600));
    when(addBlacklistEntryUseCase.execute(any())).thenReturn(mock(BlacklistEntryDto.class));

    mockMvc
        .perform(
            post("/api/v1/admin/risk/blacklist")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    verify(addBlacklistEntryUseCase).execute(any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldListBlacklist() throws Exception {
    when(listBlacklistUseCase.execute(any()))
        .thenReturn(org.springframework.data.domain.Page.empty());
    mockMvc.perform(get("/api/v1/admin/risk/blacklist")).andExpect(status().isOk());
    verify(listBlacklistUseCase).execute(any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldRemoveBlacklistEntry() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(delete("/api/v1/admin/risk/blacklist/" + id).with(csrf()))
        .andExpect(status().isOk());
    verify(removeBlacklistEntryUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldGetProfile() throws Exception {
    UUID id = UUID.randomUUID();
    when(getMerchantRiskProfileUseCase.execute(id)).thenReturn(mock(MerchantRiskProfileDto.class));
    mockMvc.perform(get("/api/v1/admin/risk/profiles/" + id)).andExpect(status().isOk());
    verify(getMerchantRiskProfileUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldUpsertProfile() throws Exception {
    UUID id = UUID.randomUUID();
    UpsertMerchantRiskProfileRequest request =
        new UpsertMerchantRiskProfileRequest(TrustLevel.TRUSTED, "{}");
    when(upsertMerchantRiskProfileUseCase.execute(eq(id), any()))
        .thenReturn(mock(MerchantRiskProfileDto.class));

    mockMvc
        .perform(
            put("/api/v1/admin/risk/profiles/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(upsertMerchantRiskProfileUseCase).execute(eq(id), any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldListCases() throws Exception {
    when(listPendingCasesUseCase.execute(any(), any()))
        .thenReturn(org.springframework.data.domain.Page.empty());
    mockMvc.perform(get("/api/v1/admin/risk/cases")).andExpect(status().isOk());
    verify(listPendingCasesUseCase).execute(any(), any());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldApproveCase() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post("/api/v1/admin/risk/cases/" + id + "/approve").with(csrf()))
        .andExpect(status().isOk());
    verify(approveRiskCaseUseCase).execute(id);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void shouldRejectCase() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post("/api/v1/admin/risk/cases/" + id + "/reject").with(csrf()))
        .andExpect(status().isOk());
    verify(rejectRiskCaseUseCase).execute(id);
  }
}
