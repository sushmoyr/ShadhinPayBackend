package com.shadhinpay.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.entity.enums.UserStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {MerchantProfileMapperImpl.class, ObjectMapper.class})
@ActiveProfiles("test")
class MerchantProfileMapperTest {

  @Autowired private MerchantProfileMapper mapper;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void toDto_mapsFieldsCorrectlyAndExcludesSensitiveData() throws Exception {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier("01712345678");
    user.setIdentifierType(IdentifierType.PHONE);
    user.setPasswordHash("hashed_password");
    user.setMfaSecret("encrypted_mfa");
    user.setStatus(UserStatus.ACTIVE);

    MerchantProfile profile = new MerchantProfile();
    profile.setFullName("John Doe");
    profile.setOnboardingStatus(OnboardingStatus.REGISTERED);
    profile.setKycData("encrypted_kyc");

    MerchantOnboardingDto dto = mapper.toDto(user, profile);

    assertThat(dto.userId()).isEqualTo(user.getId());
    assertThat(dto.identifier()).isEqualTo("01712345678");
    assertThat(dto.fullName()).isEqualTo("John Doe");

    // Verify sensitive fields are not present via JSON serialization
    String json = objectMapper.writeValueAsString(dto);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = objectMapper.readValue(json, Map.class);

    assertThat(map).doesNotContainKey("passwordHash");
    assertThat(map).doesNotContainKey("mfaSecret");
    assertThat(map).doesNotContainKey("kycData");
  }
}
