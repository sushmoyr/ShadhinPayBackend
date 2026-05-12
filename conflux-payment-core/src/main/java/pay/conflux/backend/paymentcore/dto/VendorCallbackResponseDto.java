package pay.conflux.backend.paymentcore.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for {@code POST /api/v1/payments/callback/{vendor}}. The vendor reads this to
 * confirm the platform has acknowledged the customer-return ping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorCallbackResponseDto {

  private UUID transactionId;
  private String status;
}
