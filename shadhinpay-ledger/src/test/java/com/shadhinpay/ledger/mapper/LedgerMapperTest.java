package com.shadhinpay.ledger.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.dto.PostingDto;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.entity.Posting;
import com.shadhinpay.ledger.entity.PostingType;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LedgerMapperTest.TestConfig.class)
class LedgerMapperTest {

  @Configuration
  @ComponentScan(basePackageClasses = LedgerMapper.class)
  static class TestConfig {}

  @Autowired private LedgerMapper mapper;

  @Test
  void shouldMapBalanceDtoFromAccount() {
    UUID accountId = UUID.randomUUID();
    LedgerAccount account =
        new LedgerAccount(
            UUID.randomUUID(), LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(account, "id", accountId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        account, "balance", Money.of(500, "BDT"));

    BalanceDto dto = mapper.toBalanceDto(account);

    assertThat(dto.accountId()).isEqualTo(accountId);
    assertThat(dto.accountCode()).isEqualTo("MERCHANT_PAYABLE");
    assertThat(dto.accountType()).isEqualTo("LIABILITY");
    assertThat(dto.currency()).isEqualTo("BDT");
    assertThat(dto.amount()).isEqualTo("500.0000");
  }

  @Test
  void shouldMapPostingDto() {
    UUID accountId = UUID.randomUUID();
    LedgerAccount account =
        new LedgerAccount(UUID.randomUUID(), LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(account, "id", accountId);

    List<PostingRequest> dummyPostings =
        List.of(
            new PostingRequest(accountId, Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
            new PostingRequest(
                UUID.randomUUID(), Money.of(-100, "BDT"), PostingRequest.Type.CREDIT));
    JournalEntry journal =
        new JournalEntry(
            new JournalEntryRequest("PAYMENT", "src-1", "desc", dummyPostings, Instant.now()));

    Posting posting = new Posting(journal, account, Money.of(100, "BDT"), PostingType.DEBIT);

    PostingDto dto = mapper.toPostingDto(posting);

    assertThat(dto.accountId()).isEqualTo(accountId);
    assertThat(dto.accountCode()).isEqualTo("ESCROW");
    assertThat(dto.accountType()).isEqualTo("ASSET");
    assertThat(dto.amount()).isEqualTo("100.0000");
    assertThat(dto.type()).isEqualTo("DEBIT");
    assertThat(dto.currency()).isEqualTo("BDT");
  }

  @Test
  void shouldMapJournalEntryDto() {
    UUID journalId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    LedgerAccount account =
        new LedgerAccount(UUID.randomUUID(), LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(account, "id", accountId);

    List<PostingRequest> dummyPostings =
        List.of(
            new PostingRequest(accountId, Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
            new PostingRequest(
                UUID.randomUUID(), Money.of(-100, "BDT"), PostingRequest.Type.CREDIT));
    JournalEntry journal =
        new JournalEntry(
            new JournalEntryRequest(
                "PAYMENT", "src-1", "Payment captured", dummyPostings, Instant.now()));
    org.springframework.test.util.ReflectionTestUtils.setField(journal, "id", journalId);

    Posting posting = new Posting(journal, account, Money.of(100, "BDT"), PostingType.DEBIT);

    org.springframework.test.util.ReflectionTestUtils.setField(
        journal, "postings", List.of(posting));

    JournalEntryDto dto = mapper.toDto(journal);

    assertThat(dto.id()).isEqualTo(journalId);
    assertThat(dto.sourceType()).isEqualTo("PAYMENT");
    assertThat(dto.sourceId()).isEqualTo("src-1");
    assertThat(dto.description()).isEqualTo("Payment captured");
    assertThat(dto.postings()).hasSize(1);
    assertThat(dto.postings().get(0).accountCode()).isEqualTo("ESCROW");
  }

  @Test
  void versionFieldMustNotBePresentInAnyOutboundDto() {
    List<Class<?>> dtoClasses =
        List.of(
            BalanceDto.class,
            JournalEntryDto.class,
            PostingDto.class,
            com.shadhinpay.ledger.dto.TrialBalanceReportDto.class,
            com.shadhinpay.ledger.dto.AccountIntegrityRecord.class);

    for (Class<?> dtoClass : dtoClasses) {
      List<String> fieldNames =
          Arrays.stream(dtoClass.getDeclaredFields())
              .map(Field::getName)
              .collect(Collectors.toList());

      assertThat(fieldNames)
          .as("DTO %s must NOT contain 'version' field", dtoClass.getSimpleName())
          .doesNotContain("version");
    }
  }
}
