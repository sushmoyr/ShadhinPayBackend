package pay.conflux.backend.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostingDto(
    UUID id,
    UUID accountId,
    String accountCode,
    String accountType,
    String amount,
    String type,
    String currency) {}
