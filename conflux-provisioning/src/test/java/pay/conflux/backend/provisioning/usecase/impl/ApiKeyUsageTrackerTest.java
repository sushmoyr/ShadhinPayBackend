package pay.conflux.backend.provisioning.usecase.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;

@ExtendWith(MockitoExtension.class)
class ApiKeyUsageTrackerTest {

  @Mock private ApiKeyRepository apiKeyRepository;

  @InjectMocks private ApiKeyUsageTracker tracker;

  @Test
  void recordUsage_callsRepositoryUpdate() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-11T10:00:00Z");
    when(apiKeyRepository.markLastUsedAt(id, now)).thenReturn(1);
    tracker.recordUsage(id, now);
    verify(apiKeyRepository).markLastUsedAt(id, now);
  }

  @Test
  void recordUsage_swallowsRepositoryFailure() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-11T10:00:00Z");
    when(apiKeyRepository.markLastUsedAt(eq(id), any(Instant.class)))
        .thenThrow(new RuntimeException("db down"));
    // Should not throw — fire-and-forget.
    tracker.recordUsage(id, now);
  }
}
