package pay.conflux.backend.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.money.MoneyConverter;

/**
 * Single debit or credit posting against a ledger account.
 *
 * <p>Sign convention: DEBIT amount is stored as a positive value; CREDIT amount is also stored as a
 * positive value. The {@code type} field (DEBIT/CREDIT) carries the sign meaning. For balance
 * updates, {@code LedgerAccountType.applyDelta} applies the appropriate sign:
 * asset/expense/clearing accounts increase on DEBIT, whereas liability/revenue accounts decrease on
 * DEBIT.
 */
@Entity
@Table(name = "postings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "journal_id", nullable = false, updatable = false)
  private JournalEntry journal;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private LedgerAccount account;

  @Convert(converter = MoneyConverter.class)
  @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
  private Money amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 8, updatable = false)
  private PostingType type;

  @Column(name = "currency", nullable = false, length = 3, updatable = false)
  private String currency;

  public Posting(JournalEntry journal, LedgerAccount account, Money amount, PostingType type) {
    this.journal = journal;
    this.account = account;
    this.amount = amount;
    this.type = type;
    this.currency = amount.currency();
  }
}
