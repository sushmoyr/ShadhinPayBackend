package pay.conflux.backend.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountIntegrityRecord(
    UUID accountId,
    String accountCode,
    UUID ownerId,
    String expectedBalance,
    String actualBalance,
    String delta) {}
