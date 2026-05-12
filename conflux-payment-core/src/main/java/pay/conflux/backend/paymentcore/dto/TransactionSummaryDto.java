package pay.conflux.backend.paymentcore.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionSummaryDto {
  private UUID id;
  private String status;
  private BigDecimal amount;
  private String currency;
  private String vendor;
  private String merchantOrderReference;
  private Instant createdAt;
}
