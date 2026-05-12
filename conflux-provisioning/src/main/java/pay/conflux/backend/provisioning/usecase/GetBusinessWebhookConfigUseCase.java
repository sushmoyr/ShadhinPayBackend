package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;

/**
 * Hot-path resolver for the merchant-webhook delivery target of a business.
 *
 * <p><b>Caller:</b> {@code payment-core}'s {@code WebhookOutboxDispatcher}, invoked once per
 * drained outbox row. The dispatcher must not couple to {@code BusinessRepository} (cross-feature
 * reach); this use case is the sanctioned hot-path read.
 *
 * <p>The returned secret is the decrypted webhook secret in cleartext. Callers must never log it.
 */
public interface GetBusinessWebhookConfigUseCase {

  /**
   * Resolves {@code (webhookUrl, decrypted webhookSecret)} for a business.
   *
   * @param businessId business owning the webhook target
   * @return a populated descriptor; an empty {@link BusinessWebhookConfigDescriptor} if the
   *     business has no webhook configured (the dispatcher then skips delivery rather than
   *     retrying)
   * @throws pay.conflux.backend.common.error.ResourceNotFoundException when the business does not
   *     exist
   */
  BusinessWebhookConfigDescriptor execute(UUID businessId);
}
