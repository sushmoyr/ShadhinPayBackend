package com.shadhinpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceDto(
    UUID accountId, String accountCode, String accountType, String currency, String amount) {}
