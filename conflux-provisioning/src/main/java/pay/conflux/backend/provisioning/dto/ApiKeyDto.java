package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pay.conflux.backend.provisioning.constant.Environment;

/**
 * Response DTO returned <b>exactly once</b> on API-key generation. Carries the full plaintext
 * {@code key}; subsequent lookups must return {@link ApiKeySummaryDto} instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyDto {
  private UUID id;
  private UUID businessId;
  private String key;
  private String keyPrefix;
  private String lastFour;
  private Environment environment;
  private Instant expiresAt;
}
