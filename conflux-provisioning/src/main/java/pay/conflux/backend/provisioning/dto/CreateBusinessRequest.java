package pay.conflux.backend.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBusinessRequest {

  @NotBlank private String name;

  private String displayName;

  private String logoUrl;
}
