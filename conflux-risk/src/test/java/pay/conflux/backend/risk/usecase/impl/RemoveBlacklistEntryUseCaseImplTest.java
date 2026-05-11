package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.entity.BlacklistType;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;

class RemoveBlacklistEntryUseCaseImplTest {

  @Test
  void shouldRemoveEntryAndPublishEvent() {
    BlacklistEntryRepository repo = mock(BlacklistEntryRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    RemoveBlacklistEntryUseCaseImpl useCase =
        new RemoveBlacklistEntryUseCaseImpl(repo, eventPublisher);

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
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    Object captured = captor.getValue();
    assertInstanceOf(BlacklistEntryChangedEvent.class, captured);
    BlacklistEntryChangedEvent event = (BlacklistEntryChangedEvent) captured;
    assertEquals(BlacklistType.IP, event.type());
    assertEquals("1.1.1.1", event.value());
    assertEquals(BlacklistEntryChangedEvent.ChangeKind.REMOVED, event.kind());
  }
}
