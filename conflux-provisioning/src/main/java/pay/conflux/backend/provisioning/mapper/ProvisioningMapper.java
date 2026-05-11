package pay.conflux.backend.provisioning.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import pay.conflux.backend.provisioning.dto.ApiKeySummaryDto;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.entity.VendorConfig;

/**
 * MapStruct mapper for provisioning entities. <b>Sensitive fields ({@code keyHash}, {@code
 * credentialsEncrypted}, {@code webhookSecretEncrypted}) are explicitly excluded from every
 * outbound DTO.</b>
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvisioningMapper {

  BusinessDto toDto(Business entity);

  BusinessSummaryDto toSummary(Business entity);

  @Mapping(target = "businessId", source = "businessId")
  @Mapping(target = "configured", source = "credentialsEncrypted", qualifiedByName = "isConfigured")
  VendorConfigDto toDto(VendorConfig entity);

  ApiKeySummaryDto toSummary(ApiKey entity);

  @Named("isConfigured")
  default boolean isConfigured(java.util.Map<String, String> credentialsEncrypted) {
    return credentialsEncrypted != null && !credentialsEncrypted.isEmpty();
  }
}
