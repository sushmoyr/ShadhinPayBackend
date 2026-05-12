package pay.conflux.backend.risk.engine;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.risk.enums.BlacklistType;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;

@ExtendWith(MockitoExtension.class)
class BlacklistCacheListenerTest {

  @Mock private BlacklistCache cache;

  private BlacklistCacheListener listener;

  @BeforeEach
  void setUp() {
    listener = new BlacklistCacheListener(cache);
  }

  @Test
  void added_callsCacheAdd() {
    listener.on(
        new BlacklistEntryChangedEvent(
            BlacklistType.PHONE, "+880123", BlacklistEntryChangedEvent.ChangeKind.ADDED));

    verify(cache).add(BlacklistType.PHONE, "+880123");
    verify(cache, never()).remove(any(), any());
  }

  @Test
  void removed_callsCacheRemove() {
    listener.on(
        new BlacklistEntryChangedEvent(
            BlacklistType.EMAIL, "x@y.z", BlacklistEntryChangedEvent.ChangeKind.REMOVED));

    verify(cache).remove(BlacklistType.EMAIL, "x@y.z");
    verify(cache, never()).add(any(), any());
  }

  @Test
  void exception_swallowedFailsOpen() {
    doThrow(new RuntimeException("redis down")).when(cache).add(BlacklistType.IP, "1.2.3.4");

    // must not throw
    listener.on(
        new BlacklistEntryChangedEvent(
            BlacklistType.IP, "1.2.3.4", BlacklistEntryChangedEvent.ChangeKind.ADDED));
  }
}
