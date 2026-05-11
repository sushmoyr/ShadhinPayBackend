package pay.conflux.backend.provisioning.eventlistener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pay.conflux.backend.identity.events.MerchantVerifiedEvent;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.CreateBusinessUseCase;

/**
 * On merchant KYC verification, auto-provisions the merchant's first business so that they can
 * immediately call the API. Idempotent: re-delivery of the same event is a no-op if the merchant
 * already owns a business — the modulith republish-on-restart machinery makes idempotency a
 * correctness requirement, not a nice-to-have.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantVerifiedEventListener {

  static final String DEFAULT_BUSINESS_NAME = "Default Business";

  private final BusinessRepository businessRepository;
  private final CreateBusinessUseCase createBusinessUseCase;

  @ApplicationModuleListener
  public void on(MerchantVerifiedEvent event) {
    if (businessRepository.existsByMerchantIdAndDeletedFalse(event.userId())) {
      log.info(
          "MerchantVerifiedEvent userId={} traceId={} — business already exists, skipping",
          event.userId(),
          event.traceId());
      return;
    }
    createBusinessUseCase.execute(
        event.userId(),
        new CreateBusinessRequest(DEFAULT_BUSINESS_NAME, DEFAULT_BUSINESS_NAME, null));
    log.info(
        "MerchantVerifiedEvent userId={} traceId={} — provisioned default business",
        event.userId(),
        event.traceId());
  }
}
