package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.enums.BlacklistType;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;
import pay.conflux.backend.risk.mapper.BlacklistEntryMapper;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;

class AddBlacklistEntryUseCaseImplTest {

  private BlacklistEntryRepository repository;
  private BlacklistEntryMapper mapper;
  private ApplicationEventPublisher eventPublisher;
  private AddBlacklistEntryUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    repository = mock(BlacklistEntryRepository.class);
    mapper = new BlacklistEntryMapper();
    eventPublisher = mock(ApplicationEventPublisher.class);
    useCase = new AddBlacklistEntryUseCaseImpl(repository, mapper, eventPublisher);
  }

  @Test
  void shouldAddAndPublishEvent() {
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
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Object captured = captor.getValue();
    assertInstanceOf(BlacklistEntryChangedEvent.class, captured);
    BlacklistEntryChangedEvent event = (BlacklistEntryChangedEvent) captured;
    assertEquals(BlacklistType.IP, event.type());
    assertEquals("1.1.1.1", event.value());
    assertEquals(BlacklistEntryChangedEvent.ChangeKind.ADDED, event.kind());
  }

  @Test
  void shouldThrowDuplicate() {
    AddBlacklistEntryRequest req =
        new AddBlacklistEntryRequest(BlacklistType.IP, "1.1.1.1", "reason", null);

    when(repository.findActiveByTypeAndValue(any(), any(), any()))
        .thenReturn(Optional.of(new BlacklistEntry()));

    assertThrows(DuplicateResourceException.class, () -> useCase.execute(req));
    verifyNoInteractions(eventPublisher);
  }
}
