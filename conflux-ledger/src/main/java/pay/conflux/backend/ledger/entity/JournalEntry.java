package pay.conflux.backend.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pay.conflux.backend.common.entity.Auditable;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "source_type", nullable = false, length = 32)
  private String sourceType;

  @Column(name = "source_id", nullable = false, length = 128)
  private String sourceId;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @OneToMany(mappedBy = "journal", fetch = FetchType.LAZY)
  private List<Posting> postings = new ArrayList<>();

  public JournalEntry(JournalEntryRequest request) {
    this.sourceType = request.sourceType();
    this.sourceId = request.sourceId();
    this.description = request.description();
    this.occurredAt = request.occurredAt();
  }
}
