package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;

/**
 * Updates the {@code webhookUrl} on a business. When {@code rotateSecret} is true, the encrypted
 * {@code webhookSecret} is rotated and a PING event is enqueued to the webhook outbox.
 */
public interface UpdateWebhookUseCase {

  BusinessDto execute(UUID businessId, UpdateWebhookRequest request, boolean rotateSecret);
}
