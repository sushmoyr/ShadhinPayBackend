package pay.conflux.backend.paymentcore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/v1/payments/{id}/refund}.
 *
 * <p>{@code currency} is optional; when omitted the original transaction's currency is reused.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentRestRequest {

  @NotNull
  @DecimalMin(value = "0.0001", inclusive = true, message = "amount must be positive")
  private BigDecimal amount;

  @Size(min = 3, max = 3)
  private String currency;

  @NotBlank private String reason;
}
