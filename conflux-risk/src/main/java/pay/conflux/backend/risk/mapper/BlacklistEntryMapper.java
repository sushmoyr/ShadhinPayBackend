package pay.conflux.backend.risk.mapper;

import org.springframework.stereotype.Component;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.entity.BlacklistEntry;

@Component
public class BlacklistEntryMapper {

  public BlacklistEntryDto toDto(BlacklistEntry entity) {
    if (entity == null) {
      return null;
    }
    return new BlacklistEntryDto(
        entity.getId(),
        entity.getType(),
        entity.getValue(),
        entity.getReason(),
        entity.getExpiresAt());
  }
}
