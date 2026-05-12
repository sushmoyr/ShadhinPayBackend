package pay.conflux.backend.paymentcore.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.paymentcore.dto.VendorCallbackResponseDto;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackResult;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackUseCase;

@RestController
@RequiredArgsConstructor
public class VendorCallbackControllerImpl implements VendorCallbackController {

  private final ProcessVendorCallbackUseCase processVendorCallbackUseCase;

  @Override
  public ResponseEntity<ApiResult<VendorCallbackResponseDto>> handleCallback(
      @PathVariable("vendor") String vendor,
      @RequestBody(required = false) Map<String, String> body) {
    Map<String, String> params = body == null ? Map.of() : body;
    ProcessVendorCallbackResult result = processVendorCallbackUseCase.execute(vendor, params);
    VendorCallbackResponseDto dto =
        new VendorCallbackResponseDto(result.transactionId(), result.status());
    return ApiResult.ok(dto);
  }
}
