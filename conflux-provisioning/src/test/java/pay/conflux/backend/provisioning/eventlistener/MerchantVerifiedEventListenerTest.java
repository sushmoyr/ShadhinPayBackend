package pay.conflux.backend.provisioning.eventlistener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.identity.events.MerchantVerifiedEvent;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.CreateBusinessUseCase;

@ExtendWith(MockitoExtension.class)
class MerchantVerifiedEventListenerTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private CreateBusinessUseCase createBusinessUseCase;

  @InjectMocks private MerchantVerifiedEventListener listener;

  @Test
  void on_firstEvent_provisionsDefaultBusiness() {
    UUID userId = UUID.randomUUID();
    MerchantVerifiedEvent event =
        new MerchantVerifiedEvent(userId, UUID.randomUUID(), Instant.now(), "trace-1");
    when(businessRepository.existsByMerchantIdAndDeletedFalse(userId)).thenReturn(false);
    when(createBusinessUseCase.execute(eq(userId), any())).thenReturn(new BusinessDto());

    listener.on(event);

    verify(createBusinessUseCase, times(1))
        .execute(
            eq(userId),
            org.mockito.ArgumentMatchers.argThat(
                (CreateBusinessRequest req) ->
                    MerchantVerifiedEventListener.DEFAULT_BUSINESS_NAME.equals(req.getName())
                        && MerchantVerifiedEventListener.DEFAULT_BUSINESS_NAME.equals(
                            req.getDisplayName())));
  }

  @Test
  void on_redelivery_isNoop() {
    UUID userId = UUID.randomUUID();
    MerchantVerifiedEvent event =
        new MerchantVerifiedEvent(userId, UUID.randomUUID(), Instant.now(), "trace-2");
    when(businessRepository.existsByMerchantIdAndDeletedFalse(userId)).thenReturn(true);

    listener.on(event);

    verify(createBusinessUseCase, never()).execute(any(), any());
  }
}
