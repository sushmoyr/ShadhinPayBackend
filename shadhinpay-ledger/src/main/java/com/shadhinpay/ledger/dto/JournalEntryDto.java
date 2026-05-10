package com.shadhinpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalEntryDto(
    UUID id,
    String sourceType,
    String sourceId,
    String description,
    Instant occurredAt,
    LocalDateTime createdAt,
    List<PostingDto> postings) {}
