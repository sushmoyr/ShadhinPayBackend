package pay.conflux.backend.ledger.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.dto.PostingDto;
import pay.conflux.backend.ledger.entity.JournalEntry;
import pay.conflux.backend.ledger.entity.Posting;

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
