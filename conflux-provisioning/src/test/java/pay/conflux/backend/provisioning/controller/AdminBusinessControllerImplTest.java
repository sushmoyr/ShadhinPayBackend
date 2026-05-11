package pay.conflux.backend.provisioning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.ProvisioningRoutes;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.provisioning.usecase.GetBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.SearchBusinessesUseCase;
import pay.conflux.backend.provisioning.usecase.SetBusinessStatusUseCase;

@WebMvcTest(AdminBusinessControllerImpl.class)
@Import({TestSliceSecurityConfig.class, GlobalExceptionHandler.class})
class AdminBusinessControllerImplTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SearchBusinessesUseCase searchBusinessesUseCase;
  @MockitoBean private GetBusinessUseCase getBusinessUseCase;
  @MockitoBean private SetBusinessStatusUseCase setBusinessStatusUseCase;

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void listBusinesses_happyPath_returns200WithPagination() throws Exception {
    BusinessSummaryDto s = new BusinessSummaryDto();
    s.setId(UUID.randomUUID());
    s.setName("Shop A");
    s.setStatus(BusinessStatus.ACTIVE);
    Page<BusinessSummaryDto> page = new PageImpl<>(java.util.List.of(s));
    when(searchBusinessesUseCase.execute(any(), any())).thenReturn(page);

    mockMvc
        .perform(get(ProvisioningRoutes.ADMIN_BUSINESSES))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("Shop A"))
        .andExpect(jsonPath("$.pagination.totalElements").value(1));
  }

  @Test
  void listBusinesses_withoutAuth_returns403() throws Exception {
    mockMvc.perform(get(ProvisioningRoutes.ADMIN_BUSINESSES)).andExpect(status().isForbidden());
    verifyNoInteractions(searchBusinessesUseCase);
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void listBusinesses_withMerchantAuthority_returns403() throws Exception {
    mockMvc.perform(get(ProvisioningRoutes.ADMIN_BUSINESSES)).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void getBusiness_happyPath_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    BusinessDto dto = new BusinessDto();
    dto.setId(id);
    dto.setName("Shop A");
    when(getBusinessUseCase.execute(id)).thenReturn(dto);

    mockMvc
        .perform(get(ProvisioningRoutes.ADMIN_BUSINESS_BY_ID.replace("{id}", id.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Shop A"));
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void deactivate_happyPath_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    BusinessDto dto = new BusinessDto();
    dto.setId(id);
    dto.setStatus(BusinessStatus.INACTIVE);
    when(setBusinessStatusUseCase.execute(eq(id), eq(BusinessStatus.INACTIVE))).thenReturn(dto);

    mockMvc
        .perform(post(ProvisioningRoutes.ADMIN_BUSINESS_DEACTIVATE.replace("{id}", id.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("INACTIVE"));
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void activate_happyPath_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    BusinessDto dto = new BusinessDto();
    dto.setId(id);
    dto.setStatus(BusinessStatus.ACTIVE);
    when(setBusinessStatusUseCase.execute(eq(id), eq(BusinessStatus.ACTIVE))).thenReturn(dto);

    mockMvc
        .perform(post(ProvisioningRoutes.ADMIN_BUSINESS_ACTIVATE.replace("{id}", id.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  void deactivate_withoutAuth_returns403() throws Exception {
    UUID id = UUID.randomUUID();
    mockMvc
        .perform(post(ProvisioningRoutes.ADMIN_BUSINESS_DEACTIVATE.replace("{id}", id.toString())))
        .andExpect(status().isForbidden());
    verifyNoInteractions(setBusinessStatusUseCase);
  }
}
