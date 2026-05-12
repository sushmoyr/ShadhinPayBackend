package pay.conflux.backend.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.enums.BlacklistType;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;

@ExtendWith(MockitoExtension.class)
class BlacklistCacheTest {

  @Mock private BlacklistEntryRepository repository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SetOperations<String, String> setOperations;

  private BlacklistCache cache;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    cache = new BlacklistCache(repository, redisTemplate);
  }

  @Test
  void shouldHydrateFromRepository() {
    BlacklistEntry e = new BlacklistEntry();
    e.setType(BlacklistType.PHONE);
    e.setValue("123");

    when(repository.findAllActiveByType(eq(BlacklistType.PHONE), any())).thenReturn(List.of(e));

    cache.hydrate();

    verify(redisTemplate, atLeastOnce()).delete(anyString());
    verify(setOperations).add("risk:blacklist:PHONE", "123");
  }

  @Test
  void shouldCheckIsBlacklisted() {
    when(setOperations.isMember("risk:blacklist:EMAIL", "test@test.com")).thenReturn(true);
    when(setOperations.isMember("risk:blacklist:EMAIL", "safe@test.com")).thenReturn(false);

    assertThat(cache.isBlacklisted(BlacklistType.EMAIL, "test@test.com")).isTrue();
    assertThat(cache.isBlacklisted(BlacklistType.EMAIL, "safe@test.com")).isFalse();
  }

  @Test
  void shouldReturnFalseForNullValue() {
    assertThat(cache.isBlacklisted(BlacklistType.PHONE, null)).isFalse();
  }

  @Test
  void shouldAddValue() {
    cache.add(BlacklistType.IP, "1.2.3.4");
    verify(setOperations).add("risk:blacklist:IP", "1.2.3.4");
  }

  @Test
  void shouldNotAddNullValue() {
    cache.add(BlacklistType.IP, null);
    verifyNoInteractions(setOperations);
  }

  @Test
  void shouldRemoveValue() {
    cache.remove(BlacklistType.IP, "1.2.3.4");
    verify(setOperations).remove("risk:blacklist:IP", "1.2.3.4");
  }

  @Test
  void shouldNotRemoveNullValue() {
    cache.remove(BlacklistType.IP, null);
    verifyNoInteractions(setOperations);
  }

  @Test
  void shouldScheduledHydrate() {
    when(repository.findAllActiveByType(any(), any())).thenReturn(List.of());
    cache.scheduledHydrate();
    verify(redisTemplate, atLeastOnce()).delete(anyString());
  }
}
