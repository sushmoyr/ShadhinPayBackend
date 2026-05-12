package pay.conflux.backend.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;

class AdminProfileMapperTest {

  private final AdminProfileMapper mapper = new AdminProfileMapperImpl();

  @Test
  void toDto_projectsUserAndProfileFields() {
    UUID userId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    User user = newUser(userId);
    AdminProfile profile = newProfile(profileId, userId, AdminTier.SUPER);

    AdminProfileDto dto = mapper.toDto(user, profile);

    assertThat(dto.userId()).isEqualTo(userId);
    assertThat(dto.adminProfileId()).isEqualTo(profileId);
    assertThat(dto.identifier()).isEqualTo("admin@example.com");
    assertThat(dto.identifierType()).isEqualTo(IdentifierType.EMAIL);
    assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(dto.department()).isEqualTo("Risk");
    assertThat(dto.employeeId()).isEqualTo("EMP-001");
    assertThat(dto.adminTier()).isEqualTo(AdminTier.SUPER);
  }

  @Test
  void toSummaryDto_projectsTerseFieldsOnly() {
    UUID userId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    User user = newUser(userId);
    AdminProfile profile = newProfile(profileId, userId, AdminTier.MANAGER);

    AdminProfileSummaryDto summary = mapper.toSummaryDto(user, profile);

    assertThat(summary.userId()).isEqualTo(userId);
    assertThat(summary.adminProfileId()).isEqualTo(profileId);
    assertThat(summary.identifier()).isEqualTo("admin@example.com");
    assertThat(summary.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(summary.employeeId()).isEqualTo("EMP-001");
    assertThat(summary.adminTier()).isEqualTo(AdminTier.MANAGER);
  }

  /**
   * Compile-time tripwire: the DTO record types have no sensitive accessor methods. If a future
   * change adds {@code passwordHash}, {@code mfaSecret}, or {@code mfaEnabled} to either DTO, this
   * test fails because the field doesn't exist on the projection.
   */
  @Test
  void dtoTypes_haveNoSensitiveAccessors() {
    var dtoMethods = AdminProfileDto.class.getDeclaredMethods();
    assertThat(dtoMethods)
        .extracting(java.lang.reflect.Method::getName)
        .doesNotContain("passwordHash", "mfaSecret", "mfaEnabled");

    var summaryMethods = AdminProfileSummaryDto.class.getDeclaredMethods();
    assertThat(summaryMethods)
        .extracting(java.lang.reflect.Method::getName)
        .doesNotContain("passwordHash", "mfaSecret", "mfaEnabled");
  }

  private static User newUser(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("admin@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setStatus(UserStatus.ACTIVE);
    u.setUserType(UserType.ADMIN);
    u.setPasswordHash("hash");
    return u;
  }

  private static AdminProfile newProfile(UUID id, UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(id);
    p.setUserId(userId);
    p.setDepartment("Risk");
    p.setEmployeeId("EMP-001");
    p.setAdminTier(tier);
    return p;
  }
}
