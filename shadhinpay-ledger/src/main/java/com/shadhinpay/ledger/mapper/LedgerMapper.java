package com.shadhinpay.ledger.mapper;

import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.dto.PostingDto;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.Posting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LedgerMapper {

  JournalEntryDto toDto(JournalEntry entity);

  @Mapping(target = "accountId", source = "account.id")
  @Mapping(target = "accountCode", source = "account.code")
  @Mapping(target = "accountType", expression = "java(posting.getAccount().getType().name())")
  @Mapping(target = "amount", expression = "java(posting.getAmount().amount().toPlainString())")
  @Mapping(target = "type", expression = "java(posting.getType().name())")
  @Mapping(target = "currency", source = "currency")
  PostingDto toPostingDto(Posting posting);
}
