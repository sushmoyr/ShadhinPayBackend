package com.shadhinpay.identity.entity;

import com.shadhinpay.common.entity.AuditableAndSoftDeletable;
import com.shadhinpay.identity.entity.converter.MfaSecretConverter;
import com.shadhinpay.identity.enums.IdentifierType;
import com.shadhinpay.identity.enums.UserStatus;
import com.shadhinpay.identity.enums.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditableAndSoftDeletable {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "identifier", nullable = false)
  private String identifier;

  @Enumerated(EnumType.STRING)
  @Column(name = "identifier_type", nullable = false, length = 16)
  private IdentifierType identifierType;

  @Column(name = "password_hash", nullable = false, length = 72)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 16)
  private UserType userType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private UserStatus status;

  @Convert(converter = MfaSecretConverter.class)
  @Column(name = "mfa_secret")
  private String mfaSecret;

  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled;
}
