package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pay.conflux.backend.provisioning.constant.BusinessStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessSummaryDto {
  private UUID id;
  private String name;
  private String displayName;
  private BusinessStatus status;
}
