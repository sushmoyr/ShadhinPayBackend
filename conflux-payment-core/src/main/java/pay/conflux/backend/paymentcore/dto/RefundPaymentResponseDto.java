package pay.conflux.backend.paymentcore.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentResponseDto {

  private UUID refundTransactionId;
  private UUID originalTransactionId;
  private String status;
}
