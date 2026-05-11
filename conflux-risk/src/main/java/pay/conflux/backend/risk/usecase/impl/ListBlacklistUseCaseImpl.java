package pay.conflux.backend.risk.usecase.impl;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.mapper.BlacklistEntryMapper;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;
import pay.conflux.backend.risk.usecase.internal.ListBlacklistUseCase;

@UseCase
@RequiredArgsConstructor
public class ListBlacklistUseCaseImpl implements ListBlacklistUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final BlacklistEntryMapper blacklistEntryMapper;

  @Override
  @Transactional(readOnly = true)
  public Page<BlacklistEntryDto> execute(Pageable pageable) {
    List<BlacklistEntryDto> allActive =
        blacklistEntryRepository.findAllActive(Instant.now()).stream()
            .map(blacklistEntryMapper::toDto)
            .toList();

    int start = (int) pageable.getOffset();
    int end = Math.min((start + pageable.getPageSize()), allActive.size());

    List<BlacklistEntryDto> pageContent = start <= end ? allActive.subList(start, end) : List.of();

    return new PageImpl<>(pageContent, pageable, allActive.size());
  }
}
