package com.shadhinpay.ledger.entity;

import com.shadhinpay.common.entity.Auditable;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.common.money.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ledger_accounts",
    indexes = {
      @jakarta.persistence.Index(
          name = "uk_ledger_accounts_owner_code_shard_curr",
          columnList = "owner_id, code, shard_id, currency",
          unique = true)
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccount extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private LedgerAccountType type;

  @Column(name = "code", nullable = false, length = 64)
  private String code;

  @Column(name = "shard_id", nullable = false)
  private int shardId = 0;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "BDT";

  @Convert(converter = MoneyConverter.class)
  @Column(name = "balance", nullable = false, precision = 19, scale = 4)
  private Money balance;

  @Version
  @Column(name = "version", nullable = false)
  private long version = 0L;

  public LedgerAccount(
      UUID ownerId, LedgerAccountType type, String code, int shardId, String currency) {
    this.ownerId = ownerId;
    this.type = type;
    this.code = code;
    this.shardId = shardId;
    this.currency = currency;
    this.balance = Money.zero(currency);
  }

  public void applyPosting(Money amount, PostingType postingType) {
    this.balance = type.applyDelta(this.balance, postingType, amount);
  }
}
