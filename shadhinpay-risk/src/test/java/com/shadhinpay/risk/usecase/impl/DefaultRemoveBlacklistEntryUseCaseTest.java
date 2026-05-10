package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultRemoveBlacklistEntryUseCaseTest {

  @Test
  void shouldRemoveEntry() {
    BlacklistEntryRepository repo = mock(BlacklistEntryRepository.class);
    BlacklistCache blacklistCache = mock(BlacklistCache.class);
    DefaultRemoveBlacklistEntryUseCase useCase =
        new DefaultRemoveBlacklistEntryUseCase(repo, blacklistCache);

    UUID id = UUID.randomUUID();
    BlacklistEntry entry = new BlacklistEntry();
    entry.setId(id);
    entry.setType(BlacklistType.IP);
    entry.setValue("1.1.1.1");
    entry.setDeleted(false);

    when(repo.findById(id)).thenReturn(Optional.of(entry));

    useCase.execute(id);

    assertTrue(entry.isDeleted());
    verify(repo, times(1)).save(entry);
    verify(blacklistCache).remove(BlacklistType.IP, "1.1.1.1");
  }
}
