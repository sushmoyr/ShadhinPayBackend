package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VendorConfigDto {
  private UUID id;
  private UUID businessId;
  private Vendor vendor;
  private VendorMode mode;
  private boolean configured;
}
