package pay.conflux.backend.risk.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UuidListConverter")
class UuidListConverterTest {

  private final UuidListConverter converter = new UuidListConverter();

  @Test
  @DisplayName("null list → null column")
  void nullListToNullColumn() {
    assertNull(converter.convertToDatabaseColumn(null));
  }

  @Test
  @DisplayName("empty list → empty JSON array column")
  void emptyListToEmptyJson() {
    String result = converter.convertToDatabaseColumn(List.of());
    assertEquals("[]", result);
  }

  @Test
  @DisplayName("populated list → JSON array")
  void populatedListToJson() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    String result = converter.convertToDatabaseColumn(List.of(id1, id2));
    assertTrue(result.contains(id1.toString()));
    assertTrue(result.contains(id2.toString()));
  }

  @Test
  @DisplayName("null DB data → empty list")
  void nullDbDataToEmptyList() {
    List<UUID> result = converter.convertToEntityAttribute(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("empty DB data → empty list")
  void emptyDbDataToEmptyList() {
    List<UUID> result = converter.convertToEntityAttribute("");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("blank DB data → empty list")
  void blankDbDataToEmptyList() {
    List<UUID> result = converter.convertToEntityAttribute("   ");
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("valid JSON → list of UUIDs")
  void validJsonToList() {
    UUID id = UUID.randomUUID();
    String json = "[\"" + id + "\"]";
    List<UUID> result = converter.convertToEntityAttribute(json);
    assertEquals(1, result.size());
    assertEquals(id, result.get(0));
  }

  @Test
  @DisplayName("malformed JSON throws")
  void malformedJsonThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("{not-json"));
  }
}
