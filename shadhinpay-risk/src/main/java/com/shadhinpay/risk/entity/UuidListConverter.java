package com.shadhinpay.risk.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Converter
public class UuidListConverter implements AttributeConverter<List<UUID>, String> {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(List<UUID> attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error converting list of UUIDs to JSON", e);
    }
  }

  @Override
  public List<UUID> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.trim().isEmpty()) {
      return new ArrayList<>();
    }
    try {
      return objectMapper.readValue(dbData, new TypeReference<List<UUID>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error converting JSON to list of UUIDs", e);
    }
  }
}
