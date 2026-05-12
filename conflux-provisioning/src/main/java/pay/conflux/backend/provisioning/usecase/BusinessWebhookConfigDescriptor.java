package pay.conflux.backend.provisioning.usecase;

/**
 * Decrypted webhook-delivery target for a business.
 *
 * <p>{@link #webhookUrl()} is {@code null} when the business has not configured webhooks (the
 * dispatcher then drops the delivery rather than retrying); {@link #webhookSecret()} carries
 * cleartext bytes and must never be logged.
 */
public record BusinessWebhookConfigDescriptor(String webhookUrl, String webhookSecret) {

  public static BusinessWebhookConfigDescriptor empty() {
    return new BusinessWebhookConfigDescriptor(null, null);
  }

  public boolean isConfigured() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }
}
