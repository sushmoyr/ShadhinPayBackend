package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.mapper.BlacklistEntryMapper;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import com.shadhinpay.risk.usecase.internal.ListBlacklistUseCase;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultListBlacklistUseCase implements ListBlacklistUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final BlacklistEntryMapper blacklistEntryMapper;

  public DefaultListBlacklistUseCase(
      BlacklistEntryRepository blacklistEntryRepository,
      BlacklistEntryMapper blacklistEntryMapper) {
    this.blacklistEntryRepository = blacklistEntryRepository;
    this.blacklistEntryMapper = blacklistEntryMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<BlacklistEntryDto> execute(Pageable pageable) {
    // Note: A custom count query + paginated data fetch is better, but since this is an admin
    // dashboard
    // we fetch all active and paginate in memory or use custom spec if needed.
    // Given the constraints and simplicity, we'll fetch all active and paginate.
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
