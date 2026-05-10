package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shadhinpay.common.crypto.HmacSigner;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.LoginResponse;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.UserStatus;
import com.shadhinpay.identity.entity.enums.UserType;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private HmacSigner hmacSigner;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private AuthenticateUserUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(useCase, "tokenSecret", "my-secret");
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    ReflectionTestUtils.setField(useCase, "objectMapper", mapper);
  }

  @Test
  void execute_happyPathPhone() {
    LoginRequest request = new LoginRequest("01712345678", "password123");
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier("01712345678");
    user.setIdentifierType(IdentifierType.PHONE);
    user.setPasswordHash("hashed_password");
    user.setStatus(UserStatus.ACTIVE);
    user.setUserType(UserType.MERCHANT);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(hmacSigner.sign(any(), anyString())).thenReturn("signature");

    LoginResponse result = useCase.execute(request);

    assertThat(result.userId()).isEqualTo(user.getId());
    assertThat(result.userType()).isEqualTo(UserType.MERCHANT);
    assertThat(result.authToken()).contains(".");
  }

  @ParameterizedTest
  @CsvSource({
    "01712345678, wrong_password, password_mismatch",
    "unknown_user, password123, user_not_found"
  })
  void execute_throwsUnauthorizedForInvalidCredentials(
      String identifier, String password, String reason) {
    LoginRequest request = new LoginRequest(identifier, password);

    if ("password_mismatch".equals(reason)) {
      User user = new User();
      user.setPasswordHash("hashed");
      when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
          .thenReturn(Optional.of(user));
      when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    } else {
      when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
          .thenReturn(Optional.empty());
    }

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials");
  }

  @Test
  void execute_throwsUnauthorizedForInactiveUser() {
    LoginRequest request = new LoginRequest("01712345678", "password123");
    User user = new User();
    user.setStatus(UserStatus.BLOCKED);
    user.setPasswordHash("hashed");

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials");
  }
}
