package pay.conflux.backend.ledger.entity;

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
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.money.MoneyConverter;

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
  private int shardId;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Convert(converter = MoneyConverter.class)
  @Column(name = "balance", nullable = false, precision = 19, scale = 4)
  private Money balance;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

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
