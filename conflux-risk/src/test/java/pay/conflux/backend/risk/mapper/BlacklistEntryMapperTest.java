package pay.conflux.backend.risk.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.enums.BlacklistType;

class BlacklistEntryMapperTest {

  private final BlacklistEntryMapper mapper = new BlacklistEntryMapper();

  @Test
  void shouldMapToDto() {
    BlacklistEntry entry = new BlacklistEntry();
    entry.setId(UUID.randomUUID());
    entry.setType(BlacklistType.IP);
    entry.setValue("127.0.0.1");
    entry.setReason("spam");
    entry.setExpiresAt(Instant.now());

    BlacklistEntryDto dto = mapper.toDto(entry);

    assertNotNull(dto);
    assertEquals(entry.getId(), dto.id());
    assertEquals(entry.getType(), dto.type());
    assertEquals(entry.getValue(), dto.value());
    assertEquals(entry.getReason(), dto.reason());
    assertEquals(entry.getExpiresAt(), dto.expiresAt());
  }

  @Test
  void shouldReturnNullWhenNull() {
    assertNull(mapper.toDto(null));
  }
}
