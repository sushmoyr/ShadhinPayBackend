package com.shadhinpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationSeedIT extends AbstractLedgerIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void v1003_seeds_30_system_accounts_across_three_codes_and_ten_shards() {
    int total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE owner_id IS NULL", Integer.class);
    assertThat(total).isEqualTo(30);

    int escrowShards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE code = 'ESCROW'", Integer.class);
    assertThat(escrowShards).isEqualTo(10);

    int revenueShards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE code = 'PLATFORM_REVENUE'", Integer.class);
    assertThat(revenueShards).isEqualTo(10);

    int vendorShards =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_accounts WHERE code = 'VENDOR_PAYABLE'", Integer.class);
    assertThat(vendorShards).isEqualTo(10);
  }

  @Test
  void escrow_seed_uses_clearing_type_not_asset() {
    String type =
        jdbcTemplate.queryForObject(
            "SELECT DISTINCT type FROM ledger_accounts WHERE code = 'ESCROW'", String.class);
    assertThat(type).isEqualTo("CLEARING");
  }
}
