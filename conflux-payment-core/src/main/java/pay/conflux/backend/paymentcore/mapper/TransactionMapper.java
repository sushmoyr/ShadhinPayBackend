package pay.conflux.backend.paymentcore.mapper;

import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import pay.conflux.backend.paymentcore.dto.PaymentResponseDto;
import pay.conflux.backend.paymentcore.dto.TransactionSummaryDto;
import pay.conflux.backend.paymentcore.entity.Transaction;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public abstract class TransactionMapper {

  @Mapping(target = "transactionId", source = "id")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "redirectUrl", ignore = true)
  @Mapping(target = "amount", source = "amountValue")
  @Mapping(target = "currency", source = "amountCurrency")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toInstant")
  public abstract PaymentResponseDto toResponseDto(Transaction transaction);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "amount", source = "amountValue")
  @Mapping(target = "currency", source = "amountCurrency")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toInstant")
  public abstract TransactionSummaryDto toSummaryDto(Transaction transaction);

  @Named("toInstant")
  protected java.time.Instant toInstant(java.time.LocalDateTime value) {
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }
}
