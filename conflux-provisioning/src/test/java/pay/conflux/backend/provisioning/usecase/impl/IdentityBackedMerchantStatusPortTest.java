package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IdentityBackedMerchantStatusPortTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void isActive_returnsTrue_whenStatusActive() {
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(UUID.class)))
        .thenReturn("ACTIVE");
    assertThat(new IdentityBackedMerchantStatusPort(jdbcTemplate).isActive(UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void isActive_returnsFalse_whenStatusBlocked() {
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(UUID.class)))
        .thenReturn("BLOCKED");
    assertThat(new IdentityBackedMerchantStatusPort(jdbcTemplate).isActive(UUID.randomUUID()))
        .isFalse();
  }

  @Test
  void isActive_returnsFalse_whenUserMissing() {
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(UUID.class)))
        .thenThrow(new EmptyResultDataAccessException(1));
    assertThat(new IdentityBackedMerchantStatusPort(jdbcTemplate).isActive(UUID.randomUUID()))
        .isFalse();
  }
}
