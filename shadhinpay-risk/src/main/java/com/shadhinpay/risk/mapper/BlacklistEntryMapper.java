package com.shadhinpay.risk.mapper;

import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.entity.BlacklistEntry;
import org.springframework.stereotype.Component;

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
