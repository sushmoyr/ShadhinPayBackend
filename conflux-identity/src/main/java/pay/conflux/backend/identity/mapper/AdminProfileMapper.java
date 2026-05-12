package pay.conflux.backend.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;

/**
 * Projects ({@link User}, {@link AdminProfile}) pairs into the admin DTOs that leave the module.
 * Intentionally never mentions {@code passwordHash}, {@code mfaSecret}, or {@code mfaEnabled} —
 * those fields must not reach an admin-management response per Wave D 1b deliverable 6.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdminProfileMapper {

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "adminProfileId", source = "profile.id")
  @Mapping(target = "identifier", source = "user.identifier")
  @Mapping(target = "identifierType", source = "user.identifierType")
  @Mapping(target = "status", source = "user.status")
  @Mapping(target = "department", source = "profile.department")
  @Mapping(target = "employeeId", source = "profile.employeeId")
  @Mapping(target = "adminTier", source = "profile.adminTier")
  AdminProfileDto toDto(User user, AdminProfile profile);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "adminProfileId", source = "profile.id")
  @Mapping(target = "identifier", source = "user.identifier")
  @Mapping(target = "status", source = "user.status")
  @Mapping(target = "employeeId", source = "profile.employeeId")
  @Mapping(target = "adminTier", source = "profile.adminTier")
  AdminProfileSummaryDto toSummaryDto(User user, AdminProfile profile);
}
