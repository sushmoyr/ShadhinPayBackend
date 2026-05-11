package com.shadhinpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrialBalanceReportDto(
    boolean globalSumZero, List<AccountIntegrityRecord> balanceMismatches, Instant generatedAt) {}
