package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pay.conflux.backend.provisioning.constant.BusinessStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessDto {
  private UUID id;
  private UUID merchantId;
  private String name;
  private String displayName;
  private String logoUrl;
  private BusinessStatus status;
  private String webhookUrl;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
