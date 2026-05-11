package pay.conflux.backend.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MerchantProfileMapper {

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "status", source = "user.status")
  @Mapping(target = "identifier", source = "user.identifier")
  @Mapping(target = "identifierType", source = "user.identifierType")
  @Mapping(target = "fullName", source = "profile.fullName")
  @Mapping(target = "onboardingStatus", source = "profile.onboardingStatus")
  MerchantOnboardingDto toDto(User user, MerchantProfile profile);
}
