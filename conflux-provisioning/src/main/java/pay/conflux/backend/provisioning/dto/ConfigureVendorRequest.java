package pay.conflux.backend.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigureVendorRequest {

  @NotBlank private String vendor;

  @NotBlank private String mode;

  private Map<String, String> credentials;
}
