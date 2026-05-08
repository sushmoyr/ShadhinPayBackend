package com.shadhinpay.ledger.entity;

import com.shadhinpay.common.entity.Auditable;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  public JournalEntry(JournalEntryRequest request) {
    this.sourceType = request.sourceType();
    this.sourceId = request.sourceId();
    this.description = request.description();
    this.occurredAt = request.occurredAt();
  }
}
