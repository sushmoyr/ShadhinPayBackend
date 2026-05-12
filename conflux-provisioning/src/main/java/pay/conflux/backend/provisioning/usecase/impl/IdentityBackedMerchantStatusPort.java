package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;

/**
 * Provisioning-owned read-through to {@code users.status}. Uses raw JDBC so the module avoids a
 * compile-time dependency on the identity entity surface — the FK at the database layer is the
 * authoritative coupling.
 */
@Component
@RequiredArgsConstructor
public class IdentityBackedMerchantStatusPort implements MerchantStatusPort {

  private static final String SELECT_STATUS =
      "SELECT status FROM users WHERE id = ? AND deleted = false";

  private final JdbcTemplate jdbcTemplate;

  @Override
  public boolean isActive(UUID merchantId) {
    try {
      String status = jdbcTemplate.queryForObject(SELECT_STATUS, String.class, merchantId);
      return "ACTIVE".equals(status);
    } catch (EmptyResultDataAccessException e) {
      return false;
    }
  }
}
