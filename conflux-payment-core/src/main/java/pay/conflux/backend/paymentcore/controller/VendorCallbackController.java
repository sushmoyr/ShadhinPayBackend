package pay.conflux.backend.paymentcore.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.paymentcore.constant.PaymentCoreRoutes;
import pay.conflux.backend.paymentcore.dto.VendorCallbackResponseDto;

/**
 * Public, unauthenticated entry point for MFS customer-return pings. The vendor calls this URL
 * after the customer authorizes / cancels the payment in the vendor's checkout flow.
 *
 * <p>Vendor signature verification is the adapter's responsibility — the MOCK adapter does not
 * verify; real adapters in Wave C will populate per-vendor verification.
 */
@Tag(name = "Payments - Vendor Callback")
public interface VendorCallbackController {

  @PostMapping(PaymentCoreRoutes.PAYMENT_CALLBACK)
  ResponseEntity<ApiResult<VendorCallbackResponseDto>> handleCallback(
      String vendor, Map<String, String> body);
}
