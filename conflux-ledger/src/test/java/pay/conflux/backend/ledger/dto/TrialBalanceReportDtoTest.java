package pay.conflux.backend.ledger.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrialBalanceReportDtoTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Test
  void shouldSerializeTrialBalanceReport() throws Exception {
    TrialBalanceReportDto report =
        new TrialBalanceReportDto(true, List.of(), Instant.parse("2025-01-15T03:00:00Z"));

    String json = objectMapper.writeValueAsString(report);

    assertThat(json).contains("\"globalSumZero\":true");
    assertThat(json).contains("\"balanceMismatches\":[]");
    assertThat(json).contains("\"generatedAt\":\"2025-01-15T03:00:00Z\"");
    assertThat(json).doesNotContain("version");
  }

  @Test
  void shouldSerializeWithMismatch() throws Exception {
    AccountIntegrityRecord mismatch =
        new AccountIntegrityRecord(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "ESCROW",
            null,
            "100.0000",
            "95.0000",
            "-5.0000");

    TrialBalanceReportDto report =
        new TrialBalanceReportDto(false, List.of(mismatch), Instant.parse("2025-01-15T03:00:00Z"));

    String json = objectMapper.writeValueAsString(report);

    assertThat(json).contains("\"globalSumZero\":false");
    assertThat(json).contains("\"expectedBalance\":\"100.0000\"");
    assertThat(json).contains("\"actualBalance\":\"95.0000\"");
    assertThat(json).contains("\"delta\":\"-5.0000\"");
    assertThat(json).doesNotContain("version");
  }

  @Test
  void shouldSerializeJournalEntryWithoutVersion() throws Exception {
    JournalEntryDto dto =
        new JournalEntryDto(
            UUID.randomUUID(),
            "PAYMENT",
            "src-1",
            "Payment captured",
            Instant.now(),
            null,
            List.of());

    String json = objectMapper.writeValueAsString(dto);

    assertThat(json).doesNotContain("version");
  }

  @Test
  void shouldSerializePostingDtoWithoutVersion() throws Exception {
    PostingDto dto =
        new PostingDto(
            UUID.randomUUID(), UUID.randomUUID(), "ESCROW", "ASSET", "100.0000", "DEBIT", "BDT");

    String json = objectMapper.writeValueAsString(dto);

    assertThat(json).doesNotContain("version");
  }

  @Test
  void shouldSerializeBalanceDtoWithoutVersion() throws Exception {
    BalanceDto dto =
        new BalanceDto(UUID.randomUUID(), "MERCHANT_PAYABLE", "LIABILITY", "BDT", "500.0000");

    String json = objectMapper.writeValueAsString(dto);

    assertThat(json).doesNotContain("version");
  }
}
