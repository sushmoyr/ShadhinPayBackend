package com.shadhinpay.quota.entity;

import com.shadhinpay.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "quota_usage",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"merchant_id", "period"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotaUsage extends Auditable {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "merchant_id", nullable = false, updatable = false)
  private UUID merchantId;

  @Column(name = "period", nullable = false, updatable = false, length = 7)
  private String period;

  @Column(name = "partner_mode_count", nullable = false)
  private int partnerModeCount;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public QuotaUsage(UUID id, UUID merchantId, String period) {
    this.id = id;
    this.merchantId = merchantId;
    this.period = period;
  }
}
