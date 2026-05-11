package com.shadhinpay.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.ShadhinPayApplication;
import com.shadhinpay.common.error.ErrorCode;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.identity.events.MerchantVerifiedEvent;
import com.shadhinpay.identity.events.UserBlockedEvent;
import com.shadhinpay.paymentcore.events.PaymentCompletedEvent;
import com.shadhinpay.paymentcore.events.PaymentFailedEvent;
import com.shadhinpay.paymentcore.events.PaymentInitiatedEvent;
import com.shadhinpay.paymentcore.events.PaymentRefundedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ShadhinPayApplication.class)
@Import(EventPublicationIntegrationTest.TestBeans.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class EventPublicationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("shadhinpay_events_test")
          .withUsername("test")
          .withPassword("test");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrides(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired StubPublisher publisher;
  @Autowired JdbcTemplate jdbc;

  @Test
  void all_six_events_land_in_event_publication_table() {
    publisher.publishAll();

    List<String> types =
        jdbc.queryForList("select distinct event_type from event_publication", String.class);

    assertThat(types)
        .contains(
            MerchantVerifiedEvent.class.getName(),
            UserBlockedEvent.class.getName(),
            PaymentInitiatedEvent.class.getName(),
            PaymentCompletedEvent.class.getName(),
            PaymentFailedEvent.class.getName(),
            PaymentRefundedEvent.class.getName());
  }

  @TestConfiguration
  static class TestBeans {
    @Bean
    StubPublisher stubPublisher(ApplicationEventPublisher pub) {
      return new StubPublisher(pub);
    }

    @Bean
    StubListeners stubListeners() {
      return new StubListeners();
    }
  }

  static class StubPublisher {
    private final ApplicationEventPublisher pub;

    StubPublisher(ApplicationEventPublisher pub) {
      this.pub = pub;
    }

    @Transactional
    public void publishAll() {
      Instant at = Instant.now();
      Money amount = Money.of(new BigDecimal("100.0000"), "BDT");
      Money fee = Money.of(new BigDecimal("1.0000"), "BDT");

      pub.publishEvent(
          new MerchantVerifiedEvent(UUID.randomUUID(), UUID.randomUUID(), at, "trace-1"));
      pub.publishEvent(new UserBlockedEvent(UUID.randomUUID(), "fraud", at, "trace-2"));
      pub.publishEvent(
          new PaymentInitiatedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              amount,
              "BKASH",
              "PARTNER",
              "MO-1",
              Map.of(),
              at,
              "trace-3"));
      pub.publishEvent(
          new PaymentCompletedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              amount,
              "BKASH",
              "PARTNER",
              "MO-2",
              Map.of(),
              at,
              "trace-4",
              "VTX-1",
              fee));
      pub.publishEvent(
          new PaymentFailedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              "BKASH",
              ErrorCode.MFS_ADAPTER_FAILURE,
              "vendor-down",
              Map.of(),
              at,
              "trace-5"));
      pub.publishEvent(
          new PaymentRefundedEvent(
              UUID.randomUUID(), UUID.randomUUID(), amount, Map.of(), at, "trace-6"));
    }
  }

  static class StubListeners {
    @TransactionalEventListener
    void onMerchantVerified(MerchantVerifiedEvent e) {}

    @TransactionalEventListener
    void onUserBlocked(UserBlockedEvent e) {}

    @TransactionalEventListener
    void onPaymentInitiated(PaymentInitiatedEvent e) {}

    @TransactionalEventListener
    void onPaymentCompleted(PaymentCompletedEvent e) {}

    @TransactionalEventListener
    void onPaymentFailed(PaymentFailedEvent e) {}

    @TransactionalEventListener
    void onPaymentRefunded(PaymentRefundedEvent e) {}
  }
}
