package com.shadhinpay.identity.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.crypto.HmacSigner;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.common.util.IdentifierDetector;
import com.shadhinpay.identity.dto.AuthToken;
import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.LoginResponse;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.enums.IdentifierType;
import com.shadhinpay.identity.enums.UserStatus;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.AuthenticateUserUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

  /**
   * Pre-computed BCrypt hash compared against when the lookup misses, so the failure path takes the
   * same time as a real password check. Closes the username-enumeration timing side channel.
   */
  private static final String DUMMY_HASH = new BCryptPasswordEncoder(10).encode("__dummy__");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final HmacSigner hmacSigner;
  private final ObjectMapper objectMapper;

  @Value("${shadhinpay.auth.token-secret}")
  private String tokenSecret;

  @Override
  @Transactional
  public LoginResponse execute(LoginRequest request) {
    com.shadhinpay.common.util.IdentifierType commonType =
        IdentifierDetector.detect(request.identifier());
    IdentifierType identifierType = IdentifierType.valueOf(commonType.name());

    Optional<User> userOpt =
        userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            request.identifier(), identifierType);

    String hashToCompare = userOpt.map(User::getPasswordHash).orElse(DUMMY_HASH);
    boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCompare);

    if (userOpt.isEmpty() || !passwordMatches) {
      log.warn("Login failed: invalid credentials");
      throw new UnauthorizedException("Invalid credentials");
    }

    User user = userOpt.get();
    if (user.getStatus() != UserStatus.ACTIVE) {
      log.warn("Login failed: user {} status {}", user.getId(), user.getStatus());
      throw new UnauthorizedException("Invalid credentials");
    }

    user.setLastLoginAt(OffsetDateTime.now());
    userRepository.save(user);

    String token = issueStubToken(user);
    log.info("Login success for user {}", user.getId());

    return new LoginResponse(token, user.getId(), user.getUserType());
  }

  private String issueStubToken(User user) {
    Instant now = Instant.now();
    AuthToken authToken =
        new AuthToken(user.getId(), user.getUserType(), now, now.plus(1, ChronoUnit.HOURS));

    try {
      String payloadJson = objectMapper.writeValueAsString(authToken);
      String payloadBase64 =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

      String signature = hmacSigner.sign(payloadJson.getBytes(StandardCharsets.UTF_8), tokenSecret);
      String signatureBase64 =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(signature.getBytes(StandardCharsets.UTF_8));

      return payloadBase64 + "." + signatureBase64;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize auth token", e);
    }
  }
}
