package com.shadhinpay.risk.entity;

import com.shadhinpay.common.entity.AuditableAndSoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "blacklist_entries")
@Getter
@Setter
public class BlacklistEntry extends AuditableAndSoftDeletable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BlacklistType type;

  @Column(nullable = false)
  private String value;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "expires_at")
  private Instant expiresAt;
}
