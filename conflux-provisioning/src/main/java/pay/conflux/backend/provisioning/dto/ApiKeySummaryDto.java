package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pay.conflux.backend.provisioning.constant.Environment;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeySummaryDto {
  private UUID id;
  private UUID businessId;
  private String keyPrefix;
  private String lastFour;
  private Environment environment;
  private boolean revoked;
  private Instant lastUsedAt;
  private Instant expiresAt;
}
