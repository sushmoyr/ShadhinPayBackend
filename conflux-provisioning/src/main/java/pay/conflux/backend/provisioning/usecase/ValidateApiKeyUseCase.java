package pay.conflux.backend.provisioning.usecase;

/**
 * Validates a plaintext API key. The supplied key is hashed before lookup; the plaintext is never
 * logged, persisted, or returned.
 *
 * <p>On hit, {@code lastUsedAt} is updated asynchronously to keep the hot validation path
 * non-blocking.
 */
public interface ValidateApiKeyUseCase {

  ValidatedApiKey execute(String plaintextKey);
}
