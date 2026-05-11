package pay.conflux.backend.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWebhookRequest {

  @NotBlank
  @Pattern(regexp = "^https://.*", message = "webhookUrl must use https://")
  private String webhookUrl;
}
