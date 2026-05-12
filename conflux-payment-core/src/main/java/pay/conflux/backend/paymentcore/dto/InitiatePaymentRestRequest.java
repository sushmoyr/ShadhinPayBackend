package pay.conflux.backend.paymentcore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

/**
 * Public REST body for {@code POST /api/v1/payments}.
 *
 * <p>The {@code X-Idempotency-Key} and {@code X-Business-Id} headers are read separately at the
 * controller layer (the latter is populated by the global API-key filter — Wave B 8c).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRestRequest {

  @NotNull
  @DecimalMin(value = "0.0001")
  private BigDecimal amount;

  @NotBlank
  @Pattern(regexp = "^[A-Z]{3}$")
  private String currency;

  @NotBlank private String vendor;

  @NotBlank private String merchantOrderReference;

  @URL private String callbackUrl;

  @URL private String webhookUrl;

  private Map<String, String> metadata;
}
