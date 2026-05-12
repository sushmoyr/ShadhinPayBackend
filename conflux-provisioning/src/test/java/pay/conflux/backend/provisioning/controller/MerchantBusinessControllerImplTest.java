package pay.conflux.backend.provisioning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pay.conflux.backend.common.error.ForbiddenException;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.handler.GlobalExceptionHandler;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.constant.ProvisioningRoutes;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.dto.ConfigureVendorRequest;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.testsupport.TestSliceSecurityConfig;
import pay.conflux.backend.provisioning.usecase.ConfigureVendorUseCase;
import pay.conflux.backend.provisioning.usecase.CreateBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.GetBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.ListApiKeysUseCase;
import pay.conflux.backend.provisioning.usecase.ListBusinessesUseCase;
import pay.conflux.backend.provisioning.usecase.ListVendorConfigsUseCase;
import pay.conflux.backend.provisioning.usecase.RevokeApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.RotateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.SendTestWebhookUseCase;
import pay.conflux.backend.provisioning.usecase.UpdateWebhookUseCase;
import pay.conflux.backend.provisioning.usecase.impl.BusinessOwnershipGuard;

@WebMvcTest(MerchantBusinessControllerImpl.class)
@Import({TestSliceSecurityConfig.class, GlobalExceptionHandler.class})
class MerchantBusinessControllerImplTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CreateBusinessUseCase createBusinessUseCase;
  @MockitoBean private ListBusinessesUseCase listBusinessesUseCase;
  @MockitoBean private GetBusinessUseCase getBusinessUseCase;
  @MockitoBean private ConfigureVendorUseCase configureVendorUseCase;
  @MockitoBean private ListVendorConfigsUseCase listVendorConfigsUseCase;
  @MockitoBean private GenerateApiKeyUseCase generateApiKeyUseCase;
  @MockitoBean private ListApiKeysUseCase listApiKeysUseCase;
  @MockitoBean private RotateApiKeyUseCase rotateApiKeyUseCase;
  @MockitoBean private RevokeApiKeyUseCase revokeApiKeyUseCase;
  @MockitoBean private UpdateWebhookUseCase updateWebhookUseCase;
  @MockitoBean private SendTestWebhookUseCase sendTestWebhookUseCase;
  @MockitoBean private BusinessOwnershipGuard ownershipGuard;

  private MockedStatic<SecurityUtils> securityUtilsMock;

  @BeforeEach
  void setUp() {
    securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    securityUtilsMock.close();
  }

  // ----- createBusiness -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void createBusiness_happyPath_returns201Envelope() throws Exception {
    UUID merchantId = UUID.randomUUID();
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));

    CreateBusinessRequest req = new CreateBusinessRequest("Shop A", null, null);
    BusinessDto dto = new BusinessDto();
    dto.setId(UUID.randomUUID());
    dto.setMerchantId(merchantId);
    dto.setName("Shop A");
    when(createBusinessUseCase.execute(eq(merchantId), any())).thenReturn(dto);

    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESSES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("Shop A"))
        .andExpect(jsonPath("$.meta.success").value(true));
  }

  @Test
  void createBusiness_withoutAuth_returns403() throws Exception {
    CreateBusinessRequest req = new CreateBusinessRequest("Shop A", null, null);
    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESSES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(createBusinessUseCase);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void createBusiness_withWrongAuthority_returns403() throws Exception {
    CreateBusinessRequest req = new CreateBusinessRequest("Shop A", null, null);
    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESSES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void createBusiness_withBlankName_returns400() throws Exception {
    securityUtilsMock
        .when(SecurityUtils::currentMerchantId)
        .thenReturn(Optional.of(UUID.randomUUID()));
    CreateBusinessRequest req = new CreateBusinessRequest("", null, null);
    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESSES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.meta.success").value(false))
        .andExpect(jsonPath("$.meta.errorCode").value("VALIDATION_ERROR"));
  }

  // ----- listBusinesses -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void listBusinesses_happyPath_returns200WithList() throws Exception {
    UUID merchantId = UUID.randomUUID();
    securityUtilsMock.when(SecurityUtils::currentMerchantId).thenReturn(Optional.of(merchantId));

    BusinessSummaryDto s = new BusinessSummaryDto();
    s.setId(UUID.randomUUID());
    s.setName("Shop A");
    s.setStatus(BusinessStatus.ACTIVE);
    when(listBusinessesUseCase.execute(merchantId)).thenReturn(List.of(s));

    mockMvc
        .perform(get(ProvisioningRoutes.MERCHANT_BUSINESSES))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("Shop A"));
  }

  // ----- getBusiness with tenant isolation -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void getBusiness_ownedByMerchant_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    BusinessDto dto = new BusinessDto();
    dto.setId(id);
    dto.setName("Shop A");
    when(getBusinessUseCase.execute(id)).thenReturn(dto);

    mockMvc
        .perform(get(ProvisioningRoutes.MERCHANT_BUSINESS_BY_ID.replace("{id}", id.toString())))
        .andExpect(status().isOk());
    verify(ownershipGuard).requireOwned(id);
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void getBusiness_ownedByDifferentMerchant_returns403() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new ForbiddenException("Business does not belong to the authenticated merchant"))
        .when(ownershipGuard)
        .requireOwned(id);

    mockMvc
        .perform(get(ProvisioningRoutes.MERCHANT_BUSINESS_BY_ID.replace("{id}", id.toString())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
    verifyNoInteractions(getBusinessUseCase);
  }

  // ----- configureVendor -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void configureVendor_happyPath_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(configureVendorUseCase.execute(eq(id), any())).thenReturn(new VendorConfigDto());

    ConfigureVendorRequest req = new ConfigureVendorRequest("BKASH", "PARTNER", null);
    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESS_VENDORS.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  // ----- generateApiKey -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void generateApiKey_happyPath_returns201WithPlaintextKey() throws Exception {
    UUID id = UUID.randomUUID();
    ApiKeyDto dto = new ApiKeyDto();
    dto.setId(UUID.randomUUID());
    dto.setBusinessId(id);
    dto.setKey("sp_test_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    dto.setKeyPrefix("sp_test_");
    dto.setLastFour("xxxx");
    dto.setEnvironment(Environment.TEST);
    when(generateApiKeyUseCase.execute(eq(id), any())).thenReturn(dto);

    GenerateApiKeyRequest req = new GenerateApiKeyRequest();
    req.setEnvironment("TEST");

    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESS_APIKEYS.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.key").exists());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void generateApiKey_liveEnvForNonActiveMerchant_returnsInvalidOperationState() throws Exception {
    UUID id = UUID.randomUUID();
    when(generateApiKeyUseCase.execute(eq(id), any()))
        .thenThrow(
            new InvalidOperationStateException("LIVE API keys require an ACTIVE merchant status"));

    GenerateApiKeyRequest req = new GenerateApiKeyRequest();
    req.setEnvironment("LIVE");

    mockMvc
        .perform(
            post(ProvisioningRoutes.MERCHANT_BUSINESS_APIKEYS.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.meta.errorCode").value("INVALID_OPERATION_STATE"));
  }

  // ----- rotateApiKey -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void rotateApiKey_happyPath_returnsNewPlaintext() throws Exception {
    UUID id = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    ApiKeyDto dto = new ApiKeyDto();
    dto.setId(UUID.randomUUID());
    dto.setKey("sp_test_rotated_____________________________");
    dto.setEnvironment(Environment.TEST);
    when(rotateApiKeyUseCase.execute(id, keyId)).thenReturn(dto);

    mockMvc
        .perform(
            post(
                ProvisioningRoutes.MERCHANT_BUSINESS_APIKEY_ROTATE
                    .replace("{id}", id.toString())
                    .replace("{keyId}", keyId.toString())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.key").value("sp_test_rotated_____________________________"));
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void rotateApiKey_onInactiveBusiness_returnsInvalidOperationState() throws Exception {
    UUID id = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    when(rotateApiKeyUseCase.execute(id, keyId))
        .thenThrow(
            new InvalidOperationStateException(
                "API keys can only be rotated for ACTIVE businesses"));

    mockMvc
        .perform(
            post(
                ProvisioningRoutes.MERCHANT_BUSINESS_APIKEY_ROTATE
                    .replace("{id}", id.toString())
                    .replace("{keyId}", keyId.toString())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.meta.errorCode").value("INVALID_OPERATION_STATE"));
  }

  // ----- revokeApiKey -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void revokeApiKey_happyPath_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();

    mockMvc
        .perform(
            delete(
                ProvisioningRoutes.MERCHANT_BUSINESS_APIKEY_BY_ID
                    .replace("{id}", id.toString())
                    .replace("{keyId}", keyId.toString())))
        .andExpect(status().isOk());
    verify(revokeApiKeyUseCase).execute(id, keyId);
  }

  // ----- updateWebhook -----

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void updateWebhook_httpsUrl_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateWebhookUseCase.execute(eq(id), any(), eq(true))).thenReturn(new BusinessDto());

    UpdateWebhookRequest req = new UpdateWebhookRequest("https://example.com/cb");
    mockMvc
        .perform(
            put(ProvisioningRoutes.MERCHANT_BUSINESS_WEBHOOK.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "MERCHANT")
  void updateWebhook_httpUrl_returns400() throws Exception {
    UUID id = UUID.randomUUID();
    UpdateWebhookRequest req = new UpdateWebhookRequest("http://example.com/cb");
    mockMvc
        .perform(
            put(ProvisioningRoutes.MERCHANT_BUSINESS_WEBHOOK.replace("{id}", id.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.meta.errorCode").value("VALIDATION_ERROR"));
  }
}
