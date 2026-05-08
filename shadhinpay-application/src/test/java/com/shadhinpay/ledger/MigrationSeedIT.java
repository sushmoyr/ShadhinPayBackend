package com.shadhinpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MigrationSeedIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void testSeedVerification() {
    int count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE owner_id IS NULL", Integer.class);
    assertThat(count).isEqualTo(30);

    int escrowCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE code = 'ESCROW'", Integer.class);
    assertThat(escrowCount).isEqualTo(10);
  }
}
