package pay.conflux.backend.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateApiKeyRequest {

  @NotBlank private String environment;

  private Instant expiresAt;
}
