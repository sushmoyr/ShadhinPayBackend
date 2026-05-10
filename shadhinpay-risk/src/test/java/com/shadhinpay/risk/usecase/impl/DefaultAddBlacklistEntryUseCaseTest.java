package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.mapper.BlacklistEntryMapper;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultAddBlacklistEntryUseCaseTest {

  private BlacklistEntryRepository repository;
  private BlacklistEntryMapper mapper;
  private BlacklistCache blacklistCache;
  private DefaultAddBlacklistEntryUseCase useCase;

  @BeforeEach
  void setUp() {
    repository = mock(BlacklistEntryRepository.class);
    mapper = new BlacklistEntryMapper();
    blacklistCache = mock(BlacklistCache.class);
    useCase = new DefaultAddBlacklistEntryUseCase(repository, mapper, blacklistCache);
  }

  @Test
  void shouldAddSuccessfully() {
    AddBlacklistEntryRequest req =
        new AddBlacklistEntryRequest(BlacklistType.IP, "1.1.1.1", "reason", null);

    when(repository.findActiveByTypeAndValue(any(), any(), any())).thenReturn(Optional.empty());
    when(repository.save(any(BlacklistEntry.class)))
        .thenAnswer(
            i -> {
              BlacklistEntry e = i.getArgument(0);
              e.setId(UUID.randomUUID());
              return e;
            });

    BlacklistEntryDto dto = useCase.execute(req);
    assertNotNull(dto);
    assertEquals("1.1.1.1", dto.value());
    verify(blacklistCache).add(eq(BlacklistType.IP), eq("1.1.1.1"));
  }

  @Test
  void shouldThrowDuplicate() {
    AddBlacklistEntryRequest req =
        new AddBlacklistEntryRequest(BlacklistType.IP, "1.1.1.1", "reason", null);

    when(repository.findActiveByTypeAndValue(any(), any(), any()))
        .thenReturn(Optional.of(new BlacklistEntry()));

    assertThrows(DuplicateResourceException.class, () -> useCase.execute(req));
  }
}
