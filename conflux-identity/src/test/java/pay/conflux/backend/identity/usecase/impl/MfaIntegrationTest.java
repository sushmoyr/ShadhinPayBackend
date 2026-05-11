package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.DisableMfaUseCase;
import pay.conflux.backend.identity.usecase.EnableMfaUseCase;
import pay.conflux.backend.identity.usecase.VerifyMfaUseCase;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class MfaIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private EnableMfaUseCase enableMfaUseCase;
  @Autowired private VerifyMfaUseCase verifyMfaUseCase;
  @Autowired private DisableMfaUseCase disableMfaUseCase;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
  private final TimeProvider timeProvider = new SystemTimeProvider();

  private static final String TEST_PASSWORD = "test-password-123";
  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier("01712345678");
    user.setIdentifierType(IdentifierType.PHONE);
    user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
    user.setUserType(UserType.MERCHANT);
    user.setStatus(UserStatus.ACTIVE);
    user = userRepository.save(user);
  }

  @Test
  void enableThenVerifyWithReturnedSecret_succeeds() throws CodeGenerationException {
    MfaEnableResponse response = enableMfaUseCase.execute(user.getId());

    assertThat(response.secret()).isNotBlank();
    assertThat(response.provisioningUri()).startsWith("otpauth://totp/");
    assertThat(response.qrCodeBase64()).startsWith("data:image/png;base64,");

    User updated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updated.isMfaEnabled()).isTrue();
    assertThat(updated.getMfaSecret()).isNotNull();

    long counter = timeProvider.getTime() / 30;
    String code = generateCode(response.secret(), counter);

    verifyMfaUseCase.execute(user.getId(), code);
  }

  @Test
  void enableWhenAlreadyEnabled_throwsInvalidOperationState() {
    enableMfaUseCase.execute(user.getId());

    assertThatThrownBy(() -> enableMfaUseCase.execute(user.getId()))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("MFA is already enabled");
  }

  @Test
  void disableThenEnableAgain_generatesNewSecret() {
    MfaEnableResponse firstResponse = enableMfaUseCase.execute(user.getId());
    String firstSecret = firstResponse.secret();

    disableMfaUseCase.execute(user.getId(), TEST_PASSWORD);

    User disabled = userRepository.findById(user.getId()).orElseThrow();
    assertThat(disabled.isMfaEnabled()).isFalse();

    MfaEnableResponse secondResponse = enableMfaUseCase.execute(user.getId());
    String secondSecret = secondResponse.secret();

    assertThat(secondSecret).isNotEqualTo(firstSecret);
  }

  @Test
  void verifyWithWrongCode_throwsUnauthorized() {
    enableMfaUseCase.execute(user.getId());

    assertThatThrownBy(() -> verifyMfaUseCase.execute(user.getId(), "000000"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }

  private String generateCode(String secret, long counter) {
    try {
      return codeGenerator.generate(secret, counter);
    } catch (CodeGenerationException e) {
      throw new RuntimeException(e);
    }
  }
}
