package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.TestWebhookResultDto;

/**
 * Synchronously delivers a signed {@code webhook.test} event to the business's configured webhook
 * URL and returns the outcome. Used by the merchant dashboard's "Test webhook" button to verify the
 * URL is reachable and the merchant's listener correctly validates the {@code X-PGW-Signature}
 * header.
 *
 * <p>Failure modes are all returned as a populated {@link TestWebhookResultDto} — only programmer
 * errors (no webhook URL configured, business not found) throw.
 */
public interface SendTestWebhookUseCase {

  TestWebhookResultDto execute(UUID businessId);
}
